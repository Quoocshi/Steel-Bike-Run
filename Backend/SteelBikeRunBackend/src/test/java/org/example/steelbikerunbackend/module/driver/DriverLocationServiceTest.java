package org.example.steelbikerunbackend.module.driver;

import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateRequest;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateResponse;
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

import java.util.Optional;
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
                .role(UserRole.DRIVER)
                .build();

        onlineDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-999.99")
                .isOnline(true)
                .build();

        offlineDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-000.00")
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

        // Kiểm tra response
        assertThat(resp.latitude()).isEqualTo(LAT);
        assertThat(resp.longitude()).isEqualTo(LNG);
        assertThat(resp.driverId()).isEqualTo(onlineDriver.getId().toString());
        // H3 index phải có độ dài hợp lệ (15 chars cho res=9 ở dạng address)
        assertThat(resp.h3Index()).isNotBlank();
        assertThat(resp.updatedAt()).isNotNull();

        // Đảm bảo Redis được ghi đúng
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

        // Kiểm tra oldH3Index được truyền vào save() để xóa khỏi ô cũ
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
        // H3 address string có dạng hex, không dấu tiền tố "0x"
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
        // Hà Nội vs TP.HCM — chắc chắn khác cell
        String hn = service.latLngToH3(21.0278, 105.8342);
        String hcm = service.latLngToH3(10.7769, 106.7009);

        assertThat(hn).isNotEqualTo(hcm);
    }
}
