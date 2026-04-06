package org.example.steelbikerunbackend.module.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.common.security.JwtUtil;
import org.example.steelbikerunbackend.module.auth.dto.AuthResponse;
import org.example.steelbikerunbackend.module.auth.dto.LoginRequest;
import org.example.steelbikerunbackend.module.auth.dto.RegisterRequest;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(request.role())
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} role={}", user.getEmail(), user.getRole());

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponse.of(token, user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Tìm user theo email hoặc phone
        User user = userRepository.findByEmail(request.identifier())
                .or(() -> userRepository.findByPhone(request.identifier()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!user.isActive()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Account is disabled");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        log.info("User logged in: {} role={}", user.getEmail(), user.getRole());

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return AuthResponse.of(token, user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
