package org.example.steelbikerunbackend.module.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.user.cache.UserProfileCacheRepository;
import org.example.steelbikerunbackend.module.user.dto.UserProfileResponse;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileCacheRepository cacheRepository;

    /**
     * Lấy profile của user đang đăng nhập.
     *
     * <p>Cache-Aside pattern:
     * <ol>
     *   <li>Kiểm tra Redis trước (HIT -> trả về ngay).</li>
     *   <li>MISS -> query PostgreSQL -> lưu vào Redis -> trả về.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String email) {

        // 1. Kiểm tra Redis
        return cacheRepository.get(email).orElseGet(() -> {

            // 2. Redis MISS -> query DB
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            UserProfileResponse response = UserProfileResponse.from(user);

            // 3. Lưu vào Redis cho lần sau
            cacheRepository.put(email, response);
            log.info("[Cache] User profile loaded from DB and cached: {}", email);

            return response;
        });
    }
}
