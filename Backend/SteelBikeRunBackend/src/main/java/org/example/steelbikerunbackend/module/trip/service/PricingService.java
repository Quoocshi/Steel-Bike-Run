package org.example.steelbikerunbackend.module.trip.service;

import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateRequest;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateResponse;
import org.example.steelbikerunbackend.module.trip.dto.SurgeZoneDto;
import org.example.steelbikerunbackend.module.trip.entity.H3SurgeZone;
import org.example.steelbikerunbackend.module.trip.repository.H3SurgeZoneRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * PricingService — tính giá cuốc xe dựa trên khoảng cách Haversine và surge multiplier theo H3.
 *
 * <h3>Công thức tính giá:</h3>
 * <pre>
 * distanceKm  = haversine(pickup, dest) × ROAD_FACTOR
 * basePrice   = BASE_FARE + distanceKm × PRICE_PER_KM
 * surge       = max(surgeMultiplier của ô H3 pickup và các ô k-ring=1 lân cận)
 * finalPrice  = round(basePrice × surge / 1000) × 1000   -- làm tròn nghìn đồng
 * </pre>
 *
 * <h3>Tại sao dùng H3 k-ring=1 (7 ô) thay vì chỉ ô pickup?</h3>
 * <p>Ô H3 resolution=9 rộng ~174m. Nếu customer đứng sát ranh giới ô thì ô bên cạnh
 * có thể có surge cao hơn. Lấy max của 7 ô lân cận đảm bảo giá phản ánh thực tế vùng xung quanh.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    // -------------------------------------------------------------------------
    // Cấu hình giá (có thể externalize ra application.properties sau)
    // -------------------------------------------------------------------------

    // Giá mở cửa (VNĐ) — tính ngay khi bắt đầu cuốc xe
    public static final BigDecimal BASE_FARE = new BigDecimal("8000");

    // Giá mỗi km (VNĐ/km)
    public static final BigDecimal PRICE_PER_KM = new BigDecimal("5500");

    // Hệ số đường thực tế so với đường chim bay (đường thực dài hơn ~35%)
    public static final double ROAD_FACTOR = 1.35;

    // Tốc độ ước tính trung bình trong đô thị (km/h) — để tính duration
    public static final double AVG_SPEED_KMH = 20.0;

    // Surge mặc định khi không có data trong DB
    public static final BigDecimal DEFAULT_SURGE = BigDecimal.ONE;

    // k-ring cho vùng surge lookup (1 = 7 ô, 2 = 19 ô)
    private static final int SURGE_KRING = 1;

    // H3 resolution=9 (~174m hexagon)
    private static final int H3_RESOLUTION = 9;

    private final H3SurgeZoneRepository surgeZoneRepository;
    private final DriverLocationService driverLocationService;

    // H3Core lazy init (khởi tạo H3 library tốn tài nguyên)
    private H3Core h3Core;

    // -------------------------------------------------------------------------
    // PUBLIC API
    // -------------------------------------------------------------------------

    /**
     * Ước tính giá chuyến đi dựa trên tọa độ pickup và destination.
     * Không lưu vào DB, chỉ trả về preview để customer xem trước khi đặt xe.
     *
     * @param request tọa độ điểm đón + điểm đến + địa chỉ
     * @return ước tính giá với đầy đủ breakdown (base, surge, final)
     */
    public PriceEstimateResponse estimate(PriceEstimateRequest request) {
        validateCoordinates(request);

        H3Core h3 = getH3Core();

        // Bước 1: Tính H3 cell của điểm đón
        String pickupH3 = h3.latLngToCellAddress(request.pickupLat(), request.pickupLng(), H3_RESOLUTION);

        // Bước 2: Tính khoảng cách và thời gian ước tính
        double distanceKm = estimateDistanceKm(
                request.pickupLat(), request.pickupLng(),
                request.destLat(),   request.destLng());
        int durationMinutes = estimateDurationMinutes(distanceKm);

        // Bước 3: Tính giá cơ bản
        BigDecimal basePrice = calculateBasePrice(distanceKm);

        // Bước 4: Lấy surge multiplier từ H3 vùng pickup
        BigDecimal surge = getSurgeMultiplier(pickupH3, h3);

        // Bước 5: Tính giá cuối và làm tròn nghìn đồng
        BigDecimal finalPrice = roundToThousand(basePrice.multiply(surge));

        boolean isSurging = surge.compareTo(BigDecimal.ONE) > 0;

        log.debug("[Pricing] pickup_h3={}, dist={}km, base={}, surge={}, final={}",
                pickupH3, String.format("%.2f", distanceKm), basePrice, surge, finalPrice);

        return new PriceEstimateResponse(
                pickupH3,
                roundDouble(distanceKm, 2),
                durationMinutes,
                basePrice,
                surge,
                finalPrice,
                isSurging
        );
    }

    /**
     * Lấy surge multiplier của một H3 cell và các ô lân cận (k-ring=1).
     * Trả về giá trị MAX để đảm bảo tính nhất quán cho customer ở rìa ô.
     *
     * <p>Public để TripService có thể gọi khi tạo trip thật (snapshot giá tại thời điểm đặt).</p>
     *
     * @param h3Index ô H3 của điểm đón
     * @param h3      H3Core instance
     * @return surge multiplier lớn nhất trong vùng (min=1.0)
     */
    public BigDecimal getSurgeMultiplier(String h3Index, H3Core h3) {
        // Lấy k-ring=1: 7 ô H3 (ô trung tâm + 6 ô lân cận)
        List<String> searchCells = h3.gridDisk(h3Index, SURGE_KRING);

        List<H3SurgeZone> zones = surgeZoneRepository.findAllByH3IndexIn(searchCells);
        if (zones.isEmpty()) {
            // Chưa có dữ liệu surge → giá bình thường
            return DEFAULT_SURGE;
        }

        // Lấy surge lớn nhất trong vùng
        return zones.stream()
                .map(z -> BigDecimal.valueOf(z.getSurgeMultiplier()))
                .max(BigDecimal::compareTo)
                .orElse(DEFAULT_SURGE);
    }

    /**
     * Trả về H3Core instance — public để TripService tái sử dụng.
     */
    public H3Core getH3Core() {
        if (h3Core == null) {
            try {
                h3Core = H3Core.newInstance();
            } catch (IOException e) {
                throw new AppException(ErrorCode.INTERNAL_ERROR,
                        "Không thể khởi tạo H3Core: " + e.getMessage());
            }
        }
        return h3Core;
    }

    // -------------------------------------------------------------------------
    // SURGE ZONES API — cung cấp dữ liệu cho mobile map layer
    // -------------------------------------------------------------------------

    /**
     * Trả về tất cả các ô H3 đang có surge > 1.0 từ DB,
     * kèm tọa độ tâm để mobile vẽ polygon trên bản đồ.
     *
     * <p>Chỉ trả về ô đang active (surge_multiplier > 1.0),
     * mobile tự render ô bình thường với màu trắng mờ.</p>
     */
    public List<SurgeZoneDto> getAllSurgeZones() {
        H3Core h3 = getH3Core();
        return surgeZoneRepository.findAll().stream()
                .map(zone -> {
                    com.uber.h3core.util.LatLng center = h3.cellToLatLng(zone.getH3Index());
                    return new SurgeZoneDto(
                            zone.getH3Index(),
                            center.lat,
                            center.lng,
                            zone.getSurgeMultiplier(),
                            zone.getActiveDrivers()
                    );
                })
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // BATCH JOB
    // -------------------------------------------------------------------------

    /**
     * Cập nhật giá cước mỗi 5 phút.
     * Nếu số lượng tài xế rảnh trong khu vực tìm kiếm < 3 thì tăng giá cước (surge = 1.5).
     * Ngược lại giữ nguyên (surge = 1.0).
     */
    @Scheduled(fixedRate = 300000)
    public void updateSurgePricing() {
        log.info("[Pricing] Bắt đầu cập nhật surge pricing định kỳ (5 phút/lần)...");
        H3Core h3 = getH3Core();

        Set<String> activeH3Cells = new HashSet<>();

        // 1. Lấy các ô từ DB (những ô đã từng có hoạt động)
        surgeZoneRepository.findAll().forEach(zone -> activeH3Cells.add(zone.getH3Index()));

        // 2. Lấy các ô từ vị trí tài xế hiện tại (quét Redis)
        driverLocationService.getRedisRepository().scanAllLocationKeys().forEach(key -> {
            String driverId = key.substring(key.lastIndexOf(":") + 1);
            driverLocationService.getRedisRepository().findByDriverId(driverId)
                    .ifPresent(cache -> activeH3Cells.add(cache.getH3Index()));
        });

        int updatedCount = 0;

        // 3. Tính toán lại surge cho từng ô
        for (String h3Index : activeH3Cells) {
            try {
                com.uber.h3core.util.LatLng center = h3.cellToLatLng(h3Index);
                
                // Sử dụng findNearbyDrivers có sẵn, truyền vào k-ring=1 (như khi tính giá)
                // Chỉ cần limit=3 là đủ để biết có < 3 hay không
                List<?> nearby = driverLocationService.findNearbyDrivers(center.lat, center.lng, SURGE_KRING, 3);
                
                float surgeMultiplier = nearby.size() < 3 ? 1.5f : 1.0f;

                H3SurgeZone zone = surgeZoneRepository.findById(h3Index)
                        .orElse(H3SurgeZone.builder().h3Index(h3Index).build());

                zone.setSurgeMultiplier(surgeMultiplier);
                zone.setActiveDrivers(nearby.size());
                surgeZoneRepository.save(zone);

                updatedCount++;
            } catch (Exception e) {
                log.error("[Pricing] Lỗi cập nhật surge cho ô {}: {}", h3Index, e.getMessage());
            }
        }

        log.info("[Pricing] Cập nhật surge pricing hoàn tất cho {} vùng.", updatedCount);
    }

    // -------------------------------------------------------------------------
    // PACKAGE-PRIVATE — dùng cho unit test
    // -------------------------------------------------------------------------

    /**
     * Tính khoảng cách ước tính theo đường thực (Haversine × ROAD_FACTOR).
     */
    public double estimateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        return haversineKm(lat1, lng1, lat2, lng2) * ROAD_FACTOR;
    }

    /**
     * Tính thời gian ước tính theo phút (làm tròn lên, tối thiểu 1 phút).
     */
    public int estimateDurationMinutes(double distanceKm) {
        int minutes = (int) Math.ceil(distanceKm / AVG_SPEED_KMH * 60);
        return Math.max(minutes, 1);
    }

    /**
     * Giá cơ bản = BASE_FARE + distanceKm × PRICE_PER_KM, làm tròn nghìn đồng.
     */
    public BigDecimal calculateBasePrice(double distanceKm) {
        BigDecimal distanceCost = PRICE_PER_KM
                .multiply(BigDecimal.valueOf(distanceKm))
                .setScale(0, RoundingMode.HALF_UP);
        return roundToThousand(BASE_FARE.add(distanceCost));
    }

    // -------------------------------------------------------------------------
    // PRIVATE helpers
    // -------------------------------------------------------------------------

    /**
     * Làm tròn đến nghìn đồng gần nhất (500 → 1000, 499 → 0)
     * vì người dùng Việt Nam quen giá làm tròn.
     */
    private BigDecimal roundToThousand(BigDecimal amount) {
        return amount
                .divide(new BigDecimal("1000"), 0, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("1000"));
    }

    /**
     * Công thức Haversine — khoảng cách đường chim bay giữa 2 tọa độ (km).
     */
    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS_KM = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Làm tròn double đến n chữ số thập phân.
     */
    private double roundDouble(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Kiểm tra tọa độ hợp lệ trước khi tính toán.
     */
    private void validateCoordinates(PriceEstimateRequest request) {
        if (request.pickupLat() == null || request.pickupLng() == null
                || request.destLat() == null || request.destLng() == null) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }
        // Bean Validation đã kiểm tra range, đây chỉ guard thêm cho null
    }
}
