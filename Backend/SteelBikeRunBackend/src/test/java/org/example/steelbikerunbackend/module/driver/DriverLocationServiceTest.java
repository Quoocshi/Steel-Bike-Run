package org.example.steelbikerunbackend.module.driver;

import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateRequest;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateResponse;
import org.example.steelbikerunbackend.module.driver.dto.NearbyDriverResponse;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRedisRepository;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverLocationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private DriverLocationRedisRepository redisRepository;

    @InjectMocks
    private DriverLocationService service;

    private User driverUser;
    private Driver onlineDriver;
    private Driver offlineDriver;

    // Tọa độ thực tế: Bến Thành, TP.HCM
    private static final double LAT = 10.7769;
    private static final double LNG = 106.7009;

    @BeforeEach
    void setUp() {
        driverUser = User.builder()
                .id(UUID.randomUUID())
                .email("driver@example.com")
                .fullName("Nguyễn Văn A")
                .role(UserRole.DRIVER)
                .build();

        onlineDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-999.99")
                .vehicleModel("Honda Wave Alpha")
                .vehicleColor("Đen")
                .rating(4.8f)
                .isOnline(true)
                .build();

        offlineDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-000.00")
                .vehicleModel("Yamaha Exciter")
                .vehicleColor("Trắng")
                .rating(4.5f)
                .isOnline(false)
                .build();
    }

    // --- updateLocation ------------------------------------------------------

    @Test
    @DisplayName("updateLocation: Thành công — ghi Redis, trả về H3 index hợp lệ")
    void updateLocation_Success() {
        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(onlineDriver));
        when(redisRepository.findByDriverId(onlineDriver.getId().toString())).thenReturn(Optional.empty());

        LocationUpdateRequest req = new LocationUpdateRequest(LAT, LNG, 90.0f, 30.5f);
        LocationUpdateResponse resp = service.updateLocation(driverUser.getEmail(), req);

        assertThat(resp.latitude()).isEqualTo(LAT);
        assertThat(resp.longitude()).isEqualTo(LNG);
        assertThat(resp.driverId()).isEqualTo(onlineDriver.getId().toString());
        assertThat(resp.h3Index()).isNotBlank();
        assertThat(resp.updatedAt()).isNotNull();

        ArgumentCaptor<DriverLocationCache> cacheCaptor = ArgumentCaptor.forClass(DriverLocationCache.class);
        verify(redisRepository).save(cacheCaptor.capture(), eq(null));

        DriverLocationCache saved = cacheCaptor.getValue();
        assertThat(saved.getLatitude()).isEqualTo(LAT);
        assertThat(saved.getLongitude()).isEqualTo(LNG);
        assertThat(saved.getHeading()).isEqualTo(90.0f);
        assertThat(saved.getSpeed()).isEqualTo(30.5f);
        assertThat(saved.isOnline()).isTrue();
        assertThat(saved.getH3Index()).isEqualTo(resp.h3Index());
    }

    @Test
    @DisplayName("updateLocation: Driver đổi ô H3 -> truyền oldH3Index vào Redis")
    void updateLocation_H3CellChanged() {
        String oldH3 = "891f1d4b2a3ffff";
        DriverLocationCache oldCache = DriverLocationCache.builder()
                .h3Index(oldH3)
                .driverId(onlineDriver.getId().toString())
                .build();

        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(onlineDriver));
        when(redisRepository.findByDriverId(onlineDriver.getId().toString())).thenReturn(Optional.of(oldCache));

        service.updateLocation(driverUser.getEmail(), new LocationUpdateRequest(LAT, LNG, null, null));

        ArgumentCaptor<String> oldH3Captor = ArgumentCaptor.forClass(String.class);
        verify(redisRepository).save(any(DriverLocationCache.class), oldH3Captor.capture());
        assertThat(oldH3Captor.getValue()).isEqualTo(oldH3);
    }

    @Test
    @DisplayName("updateLocation: Thất bại — driver offline")
    void updateLocation_FailIfOffline() {
        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(offlineDriver));

        assertThatThrownBy(() ->
                service.updateLocation(driverUser.getEmail(), new LocationUpdateRequest(LAT, LNG, null, null)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("offline");

        verifyNoInteractions(redisRepository);
    }

    @Test
    @DisplayName("updateLocation: Thất bại — user không tồn tại")
    void updateLocation_UserNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateLocation("ghost@example.com", new LocationUpdateRequest(LAT, LNG, null, null)))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("updateLocation: Thất bại — chưa có profile driver")
    void updateLocation_NoDriverProfile() {
        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateLocation(driverUser.getEmail(), new LocationUpdateRequest(LAT, LNG, null, null)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Profile Driver");
    }

    // --- removeDriverLocation -------------------------------------------------

    @Test
    @DisplayName("removeDriverLocation: Xóa Redis khi driver offline — có H3 cũ")
    void removeDriverLocation_WithExistingH3() {
        String driverId = onlineDriver.getId().toString();
        String h3 = "891f1d4b2a3ffff";
        DriverLocationCache cache = DriverLocationCache.builder().h3Index(h3).build();
        when(redisRepository.findByDriverId(driverId)).thenReturn(Optional.of(cache));

        service.removeDriverLocation(driverId);

        verify(redisRepository).delete(driverId, h3);
    }

    @Test
    @DisplayName("removeDriverLocation: Xóa Redis — không có H3 (driver chưa gửi heartbeat)")
    void removeDriverLocation_NoExistingCache() {
        String driverId = onlineDriver.getId().toString();
        when(redisRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        service.removeDriverLocation(driverId);

        verify(redisRepository).delete(driverId, null);
    }

    // --- latLngToH3 ----------------------------------------------------------

    @Test
    @DisplayName("latLngToH3: Tọa độ Bến Thành trả về H3 index không rỗng")
    void latLngToH3_ValidCoordinate() {
        String h3 = service.latLngToH3(LAT, LNG);

        assertThat(h3).isNotBlank();
        assertThat(h3).doesNotStartWith("0x");
    }

    @Test
    @DisplayName("latLngToH3: 2 cuộc gọi liên tiếp cho cùng tọa độ -> kết quả giống nhau (idempotent)")
    void latLngToH3_Idempotent() {
        String h3First  = service.latLngToH3(LAT, LNG);
        String h3Second = service.latLngToH3(LAT, LNG);

        assertThat(h3First).isEqualTo(h3Second);
    }

    @Test
    @DisplayName("latLngToH3: Tọa độ khác nhau (cách xa) -> H3 index khác nhau")
    void latLngToH3_DifferentCoords_DifferentCells() {
        String hn  = service.latLngToH3(21.0278, 105.8342);
        String hcm = service.latLngToH3(10.7769, 106.7009);

        assertThat(hn).isNotEqualTo(hcm);
    }

    // --- findNearbyDrivers ---------------------------------------------------

    @Test
    @DisplayName("findNearbyDrivers: Không có driver trong vùng -> trả về list rỗng")
    void findNearbyDrivers_NoDriversInArea() {
        when(redisRepository.findDriverIdsInH3Cells(any())).thenReturn(Set.of());

        List<NearbyDriverResponse> result = service.findNearbyDrivers(LAT, LNG, 2, 5);

        assertThat(result).isEmpty();
        // Không query DB khi Redis trả rỗng — không cần tốn I/O
        verifyNoInteractions(driverRepository);
    }

    @Test
    @DisplayName("findNearbyDrivers: Có driver online gần -> trả về đúng thông tin")
    void findNearbyDrivers_ReturnsOnlineDrivers() {
        String driverId = onlineDriver.getId().toString();

        DriverLocationCache cache = DriverLocationCache.builder()
                .driverId(driverId)
                .latitude(LAT + 0.0005)   // cách điểm đón ~0.07 km
                .longitude(LNG + 0.0005)
                .h3Index("891f1d4b2a3ffff")
                .heading(90.0f)
                .speed(20.0f)
                .isOnline(true)
                .updatedAt(Instant.now())
                .build();

        when(redisRepository.findDriverIdsInH3Cells(any())).thenReturn(Set.of(driverId));
        when(redisRepository.findAllByDriverIds(any())).thenReturn(List.of(cache));
        when(driverRepository.findAllByIdInWithUser(any())).thenReturn(List.of(onlineDriver));

        List<NearbyDriverResponse> result = service.findNearbyDrivers(LAT, LNG, 2, 5);

        assertThat(result).hasSize(1);
        NearbyDriverResponse resp = result.get(0);
        assertThat(resp.driverId()).isEqualTo(driverId);
        assertThat(resp.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(resp.vehiclePlate()).isEqualTo("51G-999.99");
        assertThat(resp.vehicleModel()).isEqualTo("Honda Wave Alpha");
        assertThat(resp.rating()).isEqualTo(4.8f);
        assertThat(resp.distanceKm()).isGreaterThan(0);
        assertThat(resp.distanceKm()).isLessThan(1.0);
        assertThat(resp.heading()).isEqualTo(90.0f);
    }

    @Test
    @DisplayName("findNearbyDrivers: Driver offline trong cache bị loại bỏ")
    void findNearbyDrivers_FiltersOutOfflineDrivers() {
        String onlineId  = onlineDriver.getId().toString();
        String offlineId = offlineDriver.getId().toString();

        DriverLocationCache onlineCache = DriverLocationCache.builder()
                .driverId(onlineId).latitude(LAT).longitude(LNG)
                .h3Index("cell1").isOnline(true).updatedAt(Instant.now()).build();
        DriverLocationCache offlineCache = DriverLocationCache.builder()
                .driverId(offlineId).latitude(LAT + 0.001).longitude(LNG)
                .h3Index("cell1").isOnline(false).updatedAt(Instant.now()).build();

        when(redisRepository.findDriverIdsInH3Cells(any())).thenReturn(Set.of(onlineId, offlineId));
        when(redisRepository.findAllByDriverIds(any())).thenReturn(List.of(onlineCache, offlineCache));
        when(driverRepository.findAllByIdInWithUser(any())).thenReturn(List.of(onlineDriver, offlineDriver));

        List<NearbyDriverResponse> result = service.findNearbyDrivers(LAT, LNG, 2, 5);

        // Driver offline bị lọc bởi cache.isOnline() check
        assertThat(result).hasSize(1);
        assertThat(result.get(0).driverId()).isEqualTo(onlineId);
    }

    @Test
    @DisplayName("findNearbyDrivers: Nhiều driver -> sort theo khoảng cách tăng dần")
    void findNearbyDrivers_SortedByDistance() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();

        User userA = User.builder().id(UUID.randomUUID()).fullName("Tài xế A").build();
        User userB = User.builder().id(UUID.randomUUID()).fullName("Tài xế B").build();

        Driver driverA = Driver.builder().id(idA).user(userA)
                .vehiclePlate("51A-111").vehicleModel("Honda").vehicleColor("Đỏ").rating(4.0f).isOnline(true).build();
        Driver driverB = Driver.builder().id(idB).user(userB)
                .vehiclePlate("51B-222").vehicleModel("Yamaha").vehicleColor("Xanh").rating(4.9f).isOnline(true).build();

        // A cách ~2km, B cách ~0.05km
        DriverLocationCache cacheA = DriverLocationCache.builder()
                .driverId(idA.toString()).latitude(LAT + 0.018).longitude(LNG)
                .h3Index("cellA").isOnline(true).updatedAt(Instant.now()).build();
        DriverLocationCache cacheB = DriverLocationCache.builder()
                .driverId(idB.toString()).latitude(LAT + 0.0005).longitude(LNG)
                .h3Index("cellB").isOnline(true).updatedAt(Instant.now()).build();

        when(redisRepository.findDriverIdsInH3Cells(any())).thenReturn(Set.of(idA.toString(), idB.toString()));
        when(redisRepository.findAllByDriverIds(any())).thenReturn(List.of(cacheA, cacheB));
        when(driverRepository.findAllByIdInWithUser(any())).thenReturn(List.of(driverA, driverB));

        List<NearbyDriverResponse> result = service.findNearbyDrivers(LAT, LNG, 2, 5);

        assertThat(result).hasSize(2);
        // B gần hơn -> đứng trước
        assertThat(result.get(0).driverId()).isEqualTo(idB.toString());
        assertThat(result.get(1).driverId()).isEqualTo(idA.toString());
        assertThat(result.get(0).distanceKm()).isLessThan(result.get(1).distanceKm());
    }

    @Test
    @DisplayName("findNearbyDrivers: Giới hạn kết quả theo tham số limit")
    void findNearbyDrivers_RespectsLimit() {
        UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID(), id3 = UUID.randomUUID();
        User u1 = User.builder().id(UUID.randomUUID()).fullName("D1").build();
        User u2 = User.builder().id(UUID.randomUUID()).fullName("D2").build();
        User u3 = User.builder().id(UUID.randomUUID()).fullName("D3").build();

        Driver d1 = Driver.builder().id(id1).user(u1).vehiclePlate("P1").vehicleModel("M").vehicleColor("C").rating(4f).isOnline(true).build();
        Driver d2 = Driver.builder().id(id2).user(u2).vehiclePlate("P2").vehicleModel("M").vehicleColor("C").rating(4f).isOnline(true).build();
        Driver d3 = Driver.builder().id(id3).user(u3).vehiclePlate("P3").vehicleModel("M").vehicleColor("C").rating(4f).isOnline(true).build();

        DriverLocationCache c1 = DriverLocationCache.builder().driverId(id1.toString()).latitude(LAT + 0.001).longitude(LNG).h3Index("x").isOnline(true).updatedAt(Instant.now()).build();
        DriverLocationCache c2 = DriverLocationCache.builder().driverId(id2.toString()).latitude(LAT + 0.002).longitude(LNG).h3Index("x").isOnline(true).updatedAt(Instant.now()).build();
        DriverLocationCache c3 = DriverLocationCache.builder().driverId(id3.toString()).latitude(LAT + 0.003).longitude(LNG).h3Index("x").isOnline(true).updatedAt(Instant.now()).build();

        when(redisRepository.findDriverIdsInH3Cells(any())).thenReturn(Set.of(id1.toString(), id2.toString(), id3.toString()));
        when(redisRepository.findAllByDriverIds(any())).thenReturn(List.of(c1, c2, c3));
        when(driverRepository.findAllByIdInWithUser(any())).thenReturn(List.of(d1, d2, d3));

        // Có 3 driver nhưng xin limit=2
        List<NearbyDriverResponse> result = service.findNearbyDrivers(LAT, LNG, 2, 2);

        assertThat(result).hasSize(2);
    }
}
