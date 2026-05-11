package org.example.steelbikerunbackend.module.trip;

import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateRequest;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateResponse;
import org.example.steelbikerunbackend.module.trip.entity.H3SurgeZone;
import org.example.steelbikerunbackend.module.trip.repository.H3SurgeZoneRepository;
import org.example.steelbikerunbackend.module.trip.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private H3SurgeZoneRepository surgeZoneRepository;

    @InjectMocks
    private PricingService pricingService;

    // Tọa độ thực tế: Bến Thành → Tân Sơn Nhất (~6.5km đường chim bay)
    private static final double PICKUP_LAT = 10.7769;
    private static final double PICKUP_LNG = 106.7009;
    private static final double DEST_LAT   = 10.8230;
    private static final double DEST_LNG   = 106.6297;
    private static final String DEST_ADDR  = "Sân bay Tân Sơn Nhất";

    private PriceEstimateRequest normalRequest;

    @BeforeEach
    void setUp() {
        normalRequest = new PriceEstimateRequest(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG, DEST_ADDR);
    }

    // -------------------------------------------------------------------------
    // estimate() — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("estimate: Không có surge → surge=1.0, finalPrice=basePrice")
    void estimate_NoSurge_FinalPriceEqualsBasePrice() {
        when(surgeZoneRepository.findAllByH3IndexIn(any())).thenReturn(List.of());

        PriceEstimateResponse result = pricingService.estimate(normalRequest);

        assertThat(result.surgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.finalPrice()).isEqualByComparingTo(result.basePrice());
        assertThat(result.isSurging()).isFalse();
    }

    @Test
    @DisplayName("estimate: Có surge 1.5x → finalPrice = basePrice × 1.5, làm tròn nghìn đồng")
    void estimate_WithSurge_FinalPriceIsMultiplied() {
        H3SurgeZone zone = H3SurgeZone.builder()
                .h3Index("891f1d4b2a3ffff")
                .surgeMultiplier(1.5f)
                .build();
        when(surgeZoneRepository.findAllByH3IndexIn(any())).thenReturn(List.of(zone));

        PriceEstimateResponse result = pricingService.estimate(normalRequest);

        // finalPrice phải là bội số của 1000
        assertThat(result.finalPrice().remainder(new BigDecimal("1000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
        // finalPrice ≈ basePrice × 1.5 (với sai số làm tròn)
        BigDecimal expected = result.basePrice()
                .multiply(new BigDecimal("1.5"))
                .divide(new BigDecimal("1000"), 0, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("1000"));
        assertThat(result.finalPrice()).isEqualByComparingTo(expected);
        assertThat(result.isSurging()).isTrue();
        assertThat(result.surgeMultiplier()).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @Test
    @DisplayName("estimate: Nhiều ô H3 có surge khác nhau → lấy MAX")
    void estimate_MultipleZones_TakesMaxSurge() {
        H3SurgeZone zone1 = H3SurgeZone.builder().h3Index("cell1").surgeMultiplier(1.2f).build();
        H3SurgeZone zone2 = H3SurgeZone.builder().h3Index("cell2").surgeMultiplier(2.0f).build();
        H3SurgeZone zone3 = H3SurgeZone.builder().h3Index("cell3").surgeMultiplier(1.8f).build();
        when(surgeZoneRepository.findAllByH3IndexIn(any())).thenReturn(List.of(zone1, zone2, zone3));

        PriceEstimateResponse result = pricingService.estimate(normalRequest);

        assertThat(result.surgeMultiplier()).isEqualByComparingTo(new BigDecimal("2.0"));
    }

    @Test
    @DisplayName("estimate: pickupH3Index không được rỗng")
    void estimate_PickupH3IndexIsNotBlank() {
        when(surgeZoneRepository.findAllByH3IndexIn(any())).thenReturn(List.of());

        PriceEstimateResponse result = pricingService.estimate(normalRequest);

        assertThat(result.pickupH3Index()).isNotBlank();
    }

    @Test
    @DisplayName("estimate: distanceKm > 0 và durationMinutes >= 1")
    void estimate_PositiveDistanceAndDuration() {
        when(surgeZoneRepository.findAllByH3IndexIn(any())).thenReturn(List.of());

        PriceEstimateResponse result = pricingService.estimate(normalRequest);

        assertThat(result.distanceKm()).isGreaterThan(0.0);
        assertThat(result.durationMinutes()).isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // estimateDistanceKm()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("estimateDistanceKm: Bến Thành → TSN ~ 8-10 km (đường thực)")
    void estimateDistanceKm_BenThanh_ToTanSonNhat() {
        double dist = pricingService.estimateDistanceKm(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG);

        // Đường chim bay ~6.5km × 1.35 = ~8.8km, cho phép sai số ±40%
        assertThat(dist).isBetween(6.0, 14.0);
    }

    @Test
    @DisplayName("estimateDistanceKm: Cùng điểm → khoảng cách = 0")
    void estimateDistanceKm_SamePoint_ReturnsZero() {
        double dist = pricingService.estimateDistanceKm(PICKUP_LAT, PICKUP_LNG, PICKUP_LAT, PICKUP_LNG);

        assertThat(dist).isLessThan(0.001); // gần bằng 0, floating point
    }

    @Test
    @DisplayName("estimateDistanceKm: Đối xứng — A→B = B→A")
    void estimateDistanceKm_IsSymmetric() {
        double ab = pricingService.estimateDistanceKm(PICKUP_LAT, PICKUP_LNG, DEST_LAT, DEST_LNG);
        double ba = pricingService.estimateDistanceKm(DEST_LAT, DEST_LNG, PICKUP_LAT, PICKUP_LNG);

        assertThat(ab).isCloseTo(ba, within(0.001));
    }

    // -------------------------------------------------------------------------
    // estimateDurationMinutes()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("estimateDurationMinutes: 20km → 60 phút (20km/h)")
    void estimateDuration_20km_Returns60min() {
        int duration = pricingService.estimateDurationMinutes(20.0);
        assertThat(duration).isEqualTo(60);
    }

    @Test
    @DisplayName("estimateDurationMinutes: 0km → tối thiểu 1 phút")
    void estimateDuration_ZeroKm_ReturnsMinimumOne() {
        int duration = pricingService.estimateDurationMinutes(0.0);
        assertThat(duration).isEqualTo(1);
    }

    @Test
    @DisplayName("estimateDurationMinutes: 5km → 15 phút (làm tròn lên)")
    void estimateDuration_5km_Returns15min() {
        int duration = pricingService.estimateDurationMinutes(5.0);
        assertThat(duration).isEqualTo(15);
    }

    // -------------------------------------------------------------------------
    // calculateBasePrice()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("calculateBasePrice: Giá phải >= BASE_FARE")
    void calculateBasePrice_AlwaysAtLeastBaseFare() {
        BigDecimal price = pricingService.calculateBasePrice(0.1);
        assertThat(price).isGreaterThanOrEqualTo(PricingService.BASE_FARE);
    }

    @Test
    @DisplayName("calculateBasePrice: Giá phải là bội số của 1000")
    void calculateBasePrice_IsMultipleOf1000() {
        BigDecimal price = pricingService.calculateBasePrice(7.3);
        assertThat(price.remainder(new BigDecimal("1000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculateBasePrice: Khoảng cách lớn hơn → giá lớn hơn")
    void calculateBasePrice_LongerDistance_HigherPrice() {
        BigDecimal shortTrip = pricingService.calculateBasePrice(2.0);
        BigDecimal longTrip  = pricingService.calculateBasePrice(15.0);
        assertThat(longTrip).isGreaterThan(shortTrip);
    }

    // -------------------------------------------------------------------------
    // validate — thiếu coordinates
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("estimate: Null coordinates → ném AppException INVALID_COORDINATES")
    void estimate_NullCoordinates_ThrowsException() {
        PriceEstimateRequest badRequest = new PriceEstimateRequest(null, PICKUP_LNG, DEST_LAT, DEST_LNG, DEST_ADDR);

        assertThatThrownBy(() -> pricingService.estimate(badRequest))
                .isInstanceOf(AppException.class);
    }
}
