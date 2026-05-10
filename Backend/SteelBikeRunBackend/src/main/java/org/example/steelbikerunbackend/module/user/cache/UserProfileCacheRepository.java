package org.example.steelbikerunbackend.module.user.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.module.user.dto.UserProfileResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache repository cho User Profile.
 *
 * <p>Key pattern: {@code user:profile:{email}}
 * <p>TTL mặc định: 10 phút.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserProfileCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "user:profile:";
    private static final Duration TTL = Duration.ofMinutes(10);

    // --- Read ----------------------------------------------
    public Optional<UserProfileResponse> get(String email) {
        String key = buildKey(email);
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw instanceof UserProfileResponse cached) {
                log.debug("[Redis HIT] user profile for {}", email);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("[Redis ERROR] get user profile {}: {}", email, e.getMessage());
        }
        log.debug("[Redis MISS] user profile for {}", email);
        return Optional.empty();
    }

    // --- Write ---------------------------------------------
    public void put(String email, UserProfileResponse profile) {
        String key = buildKey(email);
        try {
            redisTemplate.opsForValue().set(key, profile, TTL);
            log.debug("[Redis SET] user profile for {} (TTL={})", email, TTL);
        } catch (Exception e) {
            log.warn("[Redis ERROR] put user profile {}: {}", email, e.getMessage());
        }
    }

    // --- Evict ---------------------------------------------
    public void evict(String email) {
        String key = buildKey(email);
        try {
            redisTemplate.delete(key);
            log.debug("[Redis DEL] user profile for {}", email);
        } catch (Exception e) {
            log.warn("[Redis ERROR] evict user profile {}: {}", email, e.getMessage());
        }
    }

    // -------------------------------------------------------
    private String buildKey(String email) {
        return KEY_PREFIX + email;
    }
}
