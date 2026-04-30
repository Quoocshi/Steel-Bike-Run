package org.example.steelbikerunbackend.module.driver;

import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.driver.dto.DriverProfileResponse;
import org.example.steelbikerunbackend.module.driver.dto.SwitchDriverRequest;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.driver.service.DriverService;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Kiểm tra logic nghiệp vụ của DriverService với Mockito, không dùng database thực
@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private DriverService driverService;

    private User driverUser;
    private User customerUser;
    private SwitchDriverRequest switchRequest;
    private Driver existingDriver;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();

        driverUser = User.builder()
                .id(userId)
                .email("driver@example.com")
                .phone("0911111111")
                .passwordHash("hashed")
                .fullName("Nguyen Tai Xe")
                .role(UserRole.DRIVER)
                .isActive(true)
                .build();

        customerUser = User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .phone("0922222222")
                .passwordHash("hashed")
                .fullName("Nguyen Khach Hang")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();

        switchRequest = new SwitchDriverRequest(
                "51G-123.45",
                "Honda Air Blade 150",
                "Den",
                "012345678901");

        existingDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-123.45")
                .vehicleModel("Honda Air Blade 150")
                .vehicleColor("Den")
                .licenseNumber("012345678901")
                .isOnline(false)
                .rating(5.0f)
                .totalTrips(0)
                .faceScanPassed(false)
                .build();
    }

    // switchDriver - trường hợp tạo profile mới

    @Test
    @DisplayName("switchDriver tạo profile mới khi tài xế chưa có profile")
    void switchDriver_shouldCreateNewProfile_whenDriverProfileNotExist() {
        // Arrange
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.empty());
        when(driverRepository.existsByVehiclePlate("51G-123.45")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("012345678901")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenReturn(existingDriver);

        // Act
        DriverProfileResponse response = driverService.switchDriver("driver@example.com", switchRequest);

        // Assert: phải là profile mới và trạng thái online là true
        assertThat(response).isNotNull();
        assertThat(response.isNewProfile()).isTrue();
        assertThat(response.vehiclePlate()).isEqualTo("51G-123.45");

        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    @DisplayName("switchDriver toggle từ offline sang online khi đã có profile")
    void switchDriver_shouldToggleOnline_whenProfileExistsAndCurrentlyOffline() {
        // Arrange: profile đã tồn tại và đang offline
        Driver offlineDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-123.45")
                .vehicleModel("Honda Air Blade 150")
                .vehicleColor("Den")
                .licenseNumber("012345678901")
                .isOnline(false)
                .rating(5.0f)
                .totalTrips(0)
                .faceScanPassed(false)
                .build();

        Driver onlineDriver = Driver.builder()
                .id(offlineDriver.getId())
                .user(driverUser)
                .vehiclePlate("51G-123.45")
                .vehicleModel("Honda Air Blade 150")
                .vehicleColor("Den")
                .licenseNumber("012345678901")
                .isOnline(true)
                .rating(5.0f)
                .totalTrips(0)
                .faceScanPassed(false)
                .build();

        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(offlineDriver));
        when(driverRepository.save(offlineDriver)).thenReturn(onlineDriver);

        // Act
        DriverProfileResponse response = driverService.switchDriver("driver@example.com", switchRequest);

        // Assert: phải chuyển sang online và không phải profile mới
        assertThat(response.isOnline()).isTrue();
        assertThat(response.isNewProfile()).isFalse();
    }

    @Test
    @DisplayName("switchDriver toggle từ online sang offline khi đã có profile và đang online")
    void switchDriver_shouldToggleOffline_whenProfileExistsAndCurrentlyOnline() {
        // Arrange: profile đã tồn tại và đang online
        Driver onlineDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-123.45")
                .vehicleModel("Honda Air Blade 150")
                .vehicleColor("Den")
                .licenseNumber("012345678901")
                .isOnline(true)
                .rating(5.0f)
                .totalTrips(0)
                .faceScanPassed(false)
                .build();

        Driver offlineDriver = Driver.builder()
                .id(onlineDriver.getId())
                .user(driverUser)
                .vehiclePlate("51G-123.45")
                .vehicleModel("Honda Air Blade 150")
                .vehicleColor("Den")
                .licenseNumber("012345678901")
                .isOnline(false)
                .rating(5.0f)
                .totalTrips(0)
                .faceScanPassed(false)
                .build();

        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(onlineDriver));
        when(driverRepository.save(onlineDriver)).thenReturn(offlineDriver);

        // Act
        DriverProfileResponse response = driverService.switchDriver("driver@example.com", switchRequest);

        // Assert: phải chuyển sang offline
        assertThat(response.isOnline()).isFalse();
        assertThat(response.isNewProfile()).isFalse();
    }

    @Test
    @DisplayName("switchDriver thất bại khi user không tồn tại")
    void switchDriver_shouldThrowUserNotFound_whenUserDoesNotExist() {
        // Arrange
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // Act and Assert
        assertThatThrownBy(() -> driverService.switchDriver("notfound@example.com", switchRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("switchDriver thất bại khi user không có role DRIVER")
    void switchDriver_shouldThrowAccessDenied_whenUserIsNotDriver() {
        // Arrange: user này có role CUSTOMER, không phải DRIVER
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customerUser));

        // Act and Assert
        assertThatThrownBy(() -> driverService.switchDriver("customer@example.com", switchRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });

        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("switchDriver thất bại khi biển số xe đã được đăng ký")
    void switchDriver_shouldThrowBadRequest_whenVehiclePlateDuplicated() {
        // Arrange: profile chưa tồn tại nhưng biển số đã có người dùng
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.empty());
        when(driverRepository.existsByVehiclePlate("51G-123.45")).thenReturn(true);

        // Act and Assert
        assertThatThrownBy(() -> driverService.switchDriver("driver@example.com", switchRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                });

        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("switchDriver thất bại khi số bằng lái đã được đăng ký")
    void switchDriver_shouldThrowBadRequest_whenLicenseNumberDuplicated() {
        // Arrange: biển số chưa dùng nhưng số bằng lái đã tồn tại
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.empty());
        when(driverRepository.existsByVehiclePlate("51G-123.45")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("012345678901")).thenReturn(true);

        // Act and Assert
        assertThatThrownBy(() -> driverService.switchDriver("driver@example.com", switchRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("switchDriver thất bại khi request null mà chưa có profile")
    void switchDriver_shouldThrowBadRequest_whenRequestNullAndNoProfile() {
        // Arrange: chưa có profile nhưng không gửi thông tin xe
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.empty());

        // Act and Assert
        assertThatThrownBy(() -> driverService.switchDriver("driver@example.com", null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                });
    }

    // getMyProfile

    @Test
    @DisplayName("getMyProfile trả về thông tin đúng của tài xế")
    void getMyProfile_shouldReturnCorrectProfile() {
        // Arrange
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(existingDriver));

        // Act
        DriverProfileResponse response = driverService.getMyProfile("driver@example.com");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("driver@example.com");
        assertThat(response.vehiclePlate()).isEqualTo("51G-123.45");
        assertThat(response.isNewProfile()).isFalse();
    }

    @Test
    @DisplayName("getMyProfile thất bại khi user không tồn tại")
    void getMyProfile_shouldThrowUserNotFound_whenUserNotExist() {
        // Arrange
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        // Act and Assert
        assertThatThrownBy(() -> driverService.getMyProfile("notfound@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("getMyProfile thất bại khi tài xế chưa có profile")
    void getMyProfile_shouldThrowBadRequest_whenDriverProfileNotExist() {
        // Arrange: user tồn tại nhưng chưa có profile driver
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.empty());

        // Act and Assert
        assertThatThrownBy(() -> driverService.getMyProfile("driver@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                });
    }
}
