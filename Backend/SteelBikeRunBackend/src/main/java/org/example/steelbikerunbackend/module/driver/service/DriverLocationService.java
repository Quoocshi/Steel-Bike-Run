package org.example.steelbikerunbackend.module.driver.service;

import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateRequest;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateResponse;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRedisRepository;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

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
}
