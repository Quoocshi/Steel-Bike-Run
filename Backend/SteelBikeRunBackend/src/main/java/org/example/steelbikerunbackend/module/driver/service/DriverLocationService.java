package org.example.steelbikerunbackend.module.driver.service;

import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateRequest;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateResponse;
import org.example.steelbikerunbackend.module.driver.dto.NearbyDriverResponse;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRedisRepository;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrator cho việc cập nhật và đọc vị trí tài xế.
 *
 * <h3>Design chính:</h3>
 * <ul>
 *   <li><b>Write path</b> (heartbeat 3s): chỉ ghi vào Redis, KHÔNG ghi PostgreSQL.
 *       Nếu ghi Postgres ở đây sẽ block WebSocket/REST thread -> không chấp nhận được.</li>
 *   <li><b>Read path</b>: đọc từ Redis (sub-1ms), không bao giờ đọc Postgres cho location realtime.</li>
 *   <li><b>Sync</b>: chạy mỗi 30s bởi {@link DriverLocationSyncJob}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverLocationService {

    // H3 resolution 9 — hexagon ~174m cạnh, cân bằng giữa độ chính xác và số lượng cells
    private static final int H3_RESOLUTION = 9;

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final DriverLocationRedisRepository redisRepository;

    // H3Core được khởi tạo lazy để tránh IOException ở constructor
    private H3Core h3Core;

    // -------------------------------------------------------------------------
    // WRITE PATH: Driver gửi heartbeat
    // -------------------------------------------------------------------------

    /**
     * Nhận vị trí mới từ Driver app và ghi vào Redis.
     *
     * <p><b>Flow:</b>
     * <ol>
     *   <li>Validate tài xế tồn tại và đang online.</li>
     *   <li>Tính H3 cell index từ lat/lng.</li>
     *   <li>Đọc H3 cell cũ từ Redis (nếu có).</li>
     *   <li>Ghi vào Redis HASH + cập nhật H3 SET (xóa khỏi cell cũ nếu đổi cell).</li>
     *   <li>PostgreSQL KHÔNG được ghi ở đây.</li>
     * </ol>
     *
     * @param userEmail email từ JWT principal
     * @param request   lat/lng/heading/speed từ GPS
     */
    public LocationUpdateResponse updateLocation(String userEmail, LocationUpdateRequest request) {

        // Validate driver tồn tại và đang online
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                        "Profile Driver chưa tồn tại. Hãy gọi /driver/switch trước."));

        if (!driver.isOnline()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Tài xế đang offline. Hãy bật trạng thái Online trước khi gửi vị trí.");
        }

        // Tính H3 cell index từ lat/lng
        String h3Index = latLngToH3(request.latitude(), request.longitude());

        // Đọc H3 cell cũ từ Redis (để xóa khỏi cell cũ nếu driver đổi cell)
        String oldH3Index = redisRepository.findByDriverId(driver.getId().toString())
                .map(DriverLocationCache::getH3Index)
                .orElse(null);

        // Build cache object và ghi vào Redis
        DriverLocationCache cache = DriverLocationCache.builder()
                .driverId(driver.getId().toString())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .h3Index(h3Index)
                .heading(request.heading())
                .speed(request.speed())
                .isOnline(true)
                .updatedAt(Instant.now())
                .build();

        redisRepository.save(cache, oldH3Index);

        log.debug("Driver [{}] location updated -> lat={}, lng={}, h3={}",
                user.getEmail(), request.latitude(), request.longitude(), h3Index);

        return new LocationUpdateResponse(
                driver.getId().toString(),
                request.latitude(),
                request.longitude(),
                h3Index,
                cache.getUpdatedAt()
        );
    }

    // -------------------------------------------------------------------------
    // CLEAN UP: Xóa location khi driver offline
    // -------------------------------------------------------------------------

    /**
     * Xóa location khỏi Redis khi tài xế offline.
     * Gọi bởi {@link DriverService} khi {@code PUT /driver/status} với isOnline=false.
     *
     * @param driverId ID tài xế
     */
    public void removeDriverLocation(String driverId) {
        // Đọc H3 hiện tại để xóa khỏi SET
        Optional<DriverLocationCache> existing = redisRepository.findByDriverId(driverId);
        String h3Index = existing.map(DriverLocationCache::getH3Index).orElse(null);
        redisRepository.delete(driverId, h3Index);
        log.info("Driver [{}] location removed from Redis (went offline)", driverId);
    }

    // -------------------------------------------------------------------------
    // READ PATH: Customer tìm tài xế gần nhất (k-ring search)
    // -------------------------------------------------------------------------

    /**
     * Tìm tối đa {@code limit} tài xế đang online gần nhất với điểm đón.
     *
     * <h3>Pipeline:</h3>
     * <ol>
     *   <li>Tính H3 cell của điểm đón (resolution=9).</li>
     *   <li>gridDisk(k=2) → 19 ô H3 lân cận.</li>
     *   <li>SUNION Redis: lấy tất cả driverId đang online trong 19 ô đó.</li>
     *   <li>Đọc DriverLocationCache từng driver (vẫn từ Redis — tươi nhất).</li>
     *   <li>Batch load Driver entity từ Postgres (1 query JOIN FETCH) — lấy tên, xe, rating.</li>
     *   <li>Tính Haversine distance → sort tăng dần → lấy top {@code limit}.</li>
     * </ol>
     *
     * @param pickupLat vĩ độ điểm đón
     * @param pickupLng kinh độ điểm đón
     * @param kRing     bán kính k-ring (mặc định 2 → 19 cells, ~350m)
     * @param limit     số driver tối đa trả về
     */
    public List<NearbyDriverResponse> findNearbyDrivers(double pickupLat, double pickupLng,
                                                        int kRing, int limit) {
        H3Core h3 = getH3Core();

        // Bước 1: cell của điểm đón
        String pickupH3 = latLngToH3(pickupLat, pickupLng);

        // Bước 2: k-ring → tập các ô lân cận (k=2 cho 19 ô, bán kính ~350m)
        List<String> searchCells = h3.gridDisk(pickupH3, kRing);
        log.debug("[NearbyDrivers] k-ring={}, pickup_h3={}, cells={}", kRing, pickupH3, searchCells.size());

        // Bước 3: SUNION Redis → Set<driverId>
        Set<Object> driverIdObjects = redisRepository.findDriverIdsInH3Cells(searchCells);
        if (driverIdObjects.isEmpty()) {
            log.debug("[NearbyDrivers] Không tìm thấy driver nào trong vùng.");
            return List.of();
        }

        // Bước 4: Đọc location cache từ Redis cho từng driver
        List<DriverLocationCache> caches = redisRepository.findAllByDriverIds(driverIdObjects);

        // Chuyển caches thành Map để join nhanh với DB result
        Map<String, DriverLocationCache> cacheByDriverId = caches.stream()
                .collect(Collectors.toMap(DriverLocationCache::getDriverId, c -> c));

        // Bước 5: Batch load Driver entity (1 query duy nhất, JOIN FETCH User)
        List<UUID> uuids = cacheByDriverId.keySet().stream()
                .map(id -> {
                    try { return UUID.fromString(id); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(id -> id != null)
                .collect(Collectors.toList());

        Map<UUID, Driver> driverMap = driverRepository.findAllByIdInWithUser(uuids).stream()
                .collect(Collectors.toMap(Driver::getId, Function.identity()));

        // Bước 6: Build response, tính Haversine, sort, lấy top limit
        return driverMap.values().stream()
                .filter(driver -> {
                    DriverLocationCache cache = cacheByDriverId.get(driver.getId().toString());
                    // Chỉ lấy driver đang online (double-check với cache)
                    return cache != null && cache.isOnline();
                })
                .map(driver -> {
                    DriverLocationCache cache = cacheByDriverId.get(driver.getId().toString());
                    double distKm = haversineKm(pickupLat, pickupLng, cache.getLatitude(), cache.getLongitude());
                    return new NearbyDriverResponse(
                            driver.getId().toString(),
                            driver.getUser().getFullName(),
                            driver.getVehiclePlate(),
                            driver.getVehicleModel(),
                            driver.getVehicleColor(),
                            driver.getRating(),
                            cache.getLatitude(),
                            cache.getLongitude(),
                            cache.getH3Index(),
                            distKm,
                            cache.getHeading(),
                            cache.getSpeed()
                    );
                })
                .sorted(Comparator.comparingDouble(NearbyDriverResponse::distanceKm))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // HELPER: H3
    // -------------------------------------------------------------------------

    /**
     * Chuyển lat/lng thành H3 cell index với resolution=9.
     * H3Core được khởi tạo lazy (tốn resource khi init).
     */
    public String latLngToH3(double lat, double lng) {
        try {
            if (h3Core == null) {
                h3Core = H3Core.newInstance();
            }
            return h3Core.latLngToCellAddress(lat, lng, H3_RESOLUTION);
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể khởi tạo H3Core: " + e.getMessage());
        }
    }

    /**
     * Trả về H3Core instance (dùng trong k-ring search của DriverLocationService).
     */
    public H3Core getH3Core() {
        try {
            if (h3Core == null) {
                h3Core = H3Core.newInstance();
            }
            return h3Core;
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể khởi tạo H3Core: " + e.getMessage());
        }
    }

    /**
     * Trả về Redis repository — dùng cho Sync Job.
     */
    public DriverLocationRedisRepository getRedisRepository() {
        return redisRepository;
    }

    // -------------------------------------------------------------------------
    // HELPER: Haversine distance
    // -------------------------------------------------------------------------

    /**
     * Tính khoảng cách giữa 2 điểm địa lý theo công thức Haversine.
     * Độ chính xác đủ cho bài toán tìm driver gần nhất (~0.5% sai số).
     *
     * @return khoảng cách tính bằng km
     */
    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS_KM = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
