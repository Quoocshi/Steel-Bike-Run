package org.example.steelbikerunbackend.module.auth;

import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.common.security.JwtUtil;
import org.example.steelbikerunbackend.module.auth.dto.AuthResponse;
import org.example.steelbikerunbackend.module.auth.dto.LoginRequest;
import org.example.steelbikerunbackend.module.auth.dto.RegisterRequest;
import org.example.steelbikerunbackend.module.auth.service.AuthService;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Kiểm tra logic nghiệp vụ của AuthService với Mockito, không dùng database thực
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest(
                "newuser@example.com",
                "0912345678",
                "password123",
                "Nguyen Van A",
                UserRole.CUSTOMER);

        savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("newuser@example.com")
                .phone("0912345678")
                .passwordHash("hashed_password")
                .fullName("Nguyen Van A")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
    }

    // register

    @Test
    @DisplayName("register thành công với thông tin hợp lệ")
    void register_shouldSucceed_withValidRequest() {
        // Arrange: email và phone chưa tồn tại, lưu user thành công, tạo token thành
        // công
        when(userRepository.existsByEmail(validRegisterRequest.email())).thenReturn(false);
        when(userRepository.existsByPhone(validRegisterRequest.phone())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-jwt-token");

        // Act
        AuthResponse response = authService.register(validRegisterRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.email()).isEqualTo("newuser@example.com");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.tokenType()).isEqualTo("Bearer");

        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("password123");
    }

    @Test
    @DisplayName("register thất bại khi email đã tồn tại")
    void register_shouldThrowEmailAlreadyExists_whenEmailTaken() {
        // Arrange: email đã được dùng bởi người dùng khác
        when(userRepository.existsByEmail(validRegisterRequest.email())).thenReturn(true);

        // Act and Assert
        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                });

        // Không được lưu user khi email đã tồn tại
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register thất bại khi số điện thoại đã tồn tại")
    void register_shouldThrowPhoneAlreadyExists_whenPhoneTaken() {
        // Arrange: email chưa dùng nhưng số điện thoại đã được đăng ký
        when(userRepository.existsByEmail(validRegisterRequest.email())).thenReturn(false);
        when(userRepository.existsByPhone(validRegisterRequest.phone())).thenReturn(true);

        // Act and Assert
        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS);
                });

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register encoder password trước khi lưu vào database")
    void register_shouldEncodePasswordBeforeSaving() {
        // Arrange
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

        // Act
        authService.register(validRegisterRequest);

        // Assert: password phải được encode trước khi lưu
        verify(passwordEncoder).encode("password123");
    }

    // login

    @Test
    @DisplayName("login thành công với email và mật khẩu đúng")
    void login_shouldSucceed_withValidEmailAndPassword() {
        // Arrange
        LoginRequest request = new LoginRequest("newuser@example.com", "password123");

        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", savedUser.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(savedUser.getEmail(), savedUser.getRole().name()))
                .thenReturn("mock-jwt-token");

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
        assertThat(response.email()).isEqualTo("newuser@example.com");
    }

    @Test
    @DisplayName("login thành công với số điện thoại thay vì email")
    void login_shouldSucceed_withPhoneAsIdentifier() {
        // Arrange: người dùng đăng nhập bằng số điện thoại
        LoginRequest request = new LoginRequest("0912345678", "password123");

        when(userRepository.findByEmail("0912345678")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", savedUser.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-jwt-token");

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertThat(response.accessToken()).isEqualTo("mock-jwt-token");
    }

    @Test
    @DisplayName("login thất bại khi không tìm thấy tài khoản")
    void login_shouldThrowInvalidCredentials_whenUserNotFound() {
        // Arrange: không tìm thấy user qua cả email lẫn phone
        LoginRequest request = new LoginRequest("unknown@example.com", "password123");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("unknown@example.com")).thenReturn(Optional.empty());

        // Act and Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                });
    }

    @Test
    @DisplayName("login thất bại khi mật khẩu sai")
    void login_shouldThrowInvalidCredentials_whenPasswordWrong() {
        // Arrange
        LoginRequest request = new LoginRequest("newuser@example.com", "wrong_password");

        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("wrong_password", savedUser.getPasswordHash())).thenReturn(false);

        // Act and Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                });
    }

    @Test
    @DisplayName("login thất bại khi tài khoản bị vô hiệu hóa")
    void login_shouldThrowUnauthorized_whenAccountDisabled() {
        // Arrange: tài khoản bị khóa (isActive = false)
        User disabledUser = User.builder()
                .id(UUID.randomUUID())
                .email("newuser@example.com")
                .phone("0912345678")
                .passwordHash("hashed_password")
                .fullName("Nguyen Van A")
                .role(UserRole.CUSTOMER)
                .isActive(false)
                .build();

        LoginRequest request = new LoginRequest("newuser@example.com", "password123");

        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.of(disabledUser));

        // Act and Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                });
    }
}
