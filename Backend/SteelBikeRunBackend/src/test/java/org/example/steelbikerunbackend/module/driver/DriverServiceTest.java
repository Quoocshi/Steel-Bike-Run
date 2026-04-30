package org.example.steelbikerunbackend.module.driver;

import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.common.security.JwtUtil;
import org.example.steelbikerunbackend.module.driver.cache.DriverProfileCacheRepository;
import org.example.steelbikerunbackend.module.driver.dto.DriverProfileResponse;
import org.example.steelbikerunbackend.module.driver.dto.DriverStatusRequest;
import org.example.steelbikerunbackend.module.driver.dto.SwitchDriverRequest;
import org.example.steelbikerunbackend.module.driver.dto.SwitchRoleResponse;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.driver.service.DriverService;
import org.example.steelbikerunbackend.module.user.cache.UserProfileCacheRepository;
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

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DriverProfileCacheRepository cacheRepository;

    @Mock
    private UserProfileCacheRepository userProfileCacheRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private DriverService driverService;

    private User customerUser;
    private User driverUser;
    private SwitchDriverRequest switchRequest;
    private Driver existingDriver;

    @BeforeEach
    void setUp() {
        customerUser = User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .build();

        driverUser = User.builder()
                .id(UUID.randomUUID())
                .email("driver@example.com")
                .role(UserRole.DRIVER)
                .build();

        switchRequest = new SwitchDriverRequest("51G-123.45", "Honda", "Black", "012345678901");

        existingDriver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-123.45")
                .isOnline(false)
                .build();
    }

    @Test
    @DisplayName("switchToDriver: Thành công tạo mới profile")
    void switchToDriver_CreateNew() {
        when(userRepository.findByEmail(customerUser.getEmail())).thenReturn(Optional.of(customerUser));
        when(driverRepository.findByUserIdWithUser(customerUser.getId())).thenReturn(Optional.empty());
        when(driverRepository.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(customerUser.getEmail(), UserRole.DRIVER.name())).thenReturn("mock-jwt-token");

        SwitchRoleResponse response = driverService.switchToDriver(customerUser.getEmail(), switchRequest);

        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.driverProfile().isOnline()).isTrue();
        assertThat(response.driverProfile().isNewProfile()).isTrue();
        verify(userRepository).save(customerUser);
    }

    @Test
    @DisplayName("switchToDriver: Thành công dùng profile cũ")
    void switchToDriver_ExistingProfile() {
        when(userRepository.findByEmail(customerUser.getEmail())).thenReturn(Optional.of(customerUser));
        when(driverRepository.findByUserIdWithUser(customerUser.getId())).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(customerUser.getEmail(), UserRole.DRIVER.name())).thenReturn("mock-jwt-token");

        SwitchRoleResponse response = driverService.switchToDriver(customerUser.getEmail(), switchRequest);

        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.driverProfile().isOnline()).isTrue();
        verify(userRepository).save(customerUser);
    }

    @Test
    @DisplayName("switchToDriver: Thất bại nếu không phải CUSTOMER")
    void switchToDriver_FailIfNotCustomer() {
        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));

        assertThatThrownBy(() -> driverService.switchToDriver(driverUser.getEmail(), switchRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Chỉ tài khoản CUSTOMER");
    }

    @Test
    @DisplayName("switchToCustomer: Thành công")
    void switchToCustomer_Success() {
        existingDriver.setOnline(true);
        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(driverUser.getEmail(), UserRole.CUSTOMER.name())).thenReturn("mock-jwt-token");

        SwitchRoleResponse response = driverService.switchToCustomer(driverUser.getEmail());

        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.driverProfile().isOnline()).isFalse();
        verify(userRepository).save(driverUser);
    }

    @Test
    @DisplayName("setOnlineStatus: Thành công cập nhật status")
    void setOnlineStatus_Success() {
        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(existingDriver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));

        DriverProfileResponse response = driverService.setOnlineStatus(driverUser.getEmail(), new DriverStatusRequest(true));

        assertThat(response.isOnline()).isTrue();
        verify(driverRepository).save(existingDriver);
    }

    @Test
    @DisplayName("getMyProfile: Lấy từ cache nếu có")
    void getMyProfile_FromCache() {
        DriverProfileResponse mockResp = mock(DriverProfileResponse.class);
        when(cacheRepository.get("driver@example.com")).thenReturn(Optional.of(mockResp));

        DriverProfileResponse result = driverService.getMyProfile("driver@example.com");

        assertThat(result).isEqualTo(mockResp);
        verifyNoInteractions(userRepository, driverRepository);
    }

    @Test
    @DisplayName("getMyProfile: Lấy từ DB nếu cache rỗng")
    void getMyProfile_FromDB() {
        when(cacheRepository.get("driver@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(driverUser.getEmail())).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(existingDriver));

        DriverProfileResponse result = driverService.getMyProfile("driver@example.com");

        assertThat(result.vehiclePlate()).isEqualTo("51G-123.45");
        verify(cacheRepository).put(eq("driver@example.com"), any(DriverProfileResponse.class));
    }
}
