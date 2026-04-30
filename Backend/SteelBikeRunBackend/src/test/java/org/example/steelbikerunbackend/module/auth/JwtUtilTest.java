package org.example.steelbikerunbackend.module.auth;

import org.example.steelbikerunbackend.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Kiểm tra các chức năng cơ bản của JwtUtil mà không cần Spring context
class JwtUtilTest {

    // Secret phải đủ 32 ký tự để dùng với HS256
    private static final String TEST_SECRET = "test-secret-key-must-be-32chars-minimum!";
    private static final long EXPIRATION_MS = 3_600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, EXPIRATION_MS);
    }

    // generateToken

    @Test
    @DisplayName("generateToken trả về token không null và không rỗng")
    void generateToken_shouldReturnNonBlankToken() {
        String token = jwtUtil.generateToken("user@example.com", "CUSTOMER");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("generateToken tạo ra token khác nhau cho email khác nhau")
    void generateToken_differentEmailsProduceDifferentTokens() {
        // JWT dùng timestamp đơn vị giây, nên hai token tạo cùng giây sẽ giống nhau
        // Thay vào đó, xác nhận rằng hai email khác nhau luôn cho ra token khác nhau
        String token1 = jwtUtil.generateToken("user1@example.com", "CUSTOMER");
        String token2 = jwtUtil.generateToken("user2@example.com", "CUSTOMER");
        assertThat(token1).isNotEqualTo(token2);
    }

    // extractEmail

    @Test
    @DisplayName("extractEmail trả về đúng email đã dùng khi tạo token")
    void extractEmail_shouldReturnCorrectEmail() {
        String email = "driver@example.com";
        String token = jwtUtil.generateToken(email, "DRIVER");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
    }

    // extractRole

    @Test
    @DisplayName("extractRole trả về đúng role đã dùng khi tạo token")
    void extractRole_shouldReturnCorrectRole() {
        String token = jwtUtil.generateToken("user@example.com", "CUSTOMER");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("extractRole trả về DRIVER khi tạo token với role DRIVER")
    void extractRole_shouldReturnDriver() {
        String token = jwtUtil.generateToken("driver@example.com", "DRIVER");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("DRIVER");
    }

    // isTokenValid

    @Test
    @DisplayName("isTokenValid trả về true với token hợp lệ")
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtUtil.generateToken("user@example.com", "CUSTOMER");
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid trả về false với token giả mạo")
    void isTokenValid_shouldReturnFalseForTamperedToken() {
        String token = jwtUtil.generateToken("user@example.com", "CUSTOMER");
        // Thêm ký tự vào cuối để làm hỏng chữ ký
        String tamperedToken = token + "abc";
        assertThat(jwtUtil.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid trả về false với chuỗi ngẫu nhiên")
    void isTokenValid_shouldReturnFalseForRandomString() {
        assertThat(jwtUtil.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid trả về false với token hết hạn")
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        // Tạo JwtUtil với thời gian hết hạn bằng 0ms để token hết hạn ngay lập tức
        JwtUtil expiredUtil = new JwtUtil(TEST_SECRET, 0L);
        String token = expiredUtil.generateToken("user@example.com", "CUSTOMER");
        assertThat(expiredUtil.isTokenValid(token)).isFalse();
    }
}
