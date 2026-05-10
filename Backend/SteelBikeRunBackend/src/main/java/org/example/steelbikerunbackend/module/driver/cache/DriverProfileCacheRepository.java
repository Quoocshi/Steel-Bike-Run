package org.example.steelbikerunbackend.module.driver.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.module.driver.dto.DriverProfileResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache repository cho Driver Profile.
 *
 * <p>Key pattern: {@code driver:profile:{email}}
 * <p>TTL mặc định: 10 phút.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DriverProfileCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "driver:profile:";
    private static final Duration TTL = Duration.ofMinutes(10);

    // --- Read ----------------------------------------------
    public Optional<DriverProfileResponse> get(String email) {
        String key = buildKey(email);
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw instanceof DriverProfileResponse cached) {
                log.debug("[Redis HIT] driver profile for {}", email);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("[Redis ERROR] get driver profile {}: {}", email, e.getMessage());
        }
        log.debug("[Redis MISS] driver profile for {}", email);
        return Optional.empty();
    }

    // --- Write ---------------------------------------------
    public void put(String email, DriverProfileResponse profile) {
        String key = buildKey(email);
        try {
            redisTemplate.opsForValue().set(key, profile, TTL);
            log.debug("[Redis SET] driver profile for {} (TTL={})", email, TTL);
        } catch (Exception e) {
            log.warn("[Redis ERROR] put driver profile {}: {}", email, e.getMessage());
        }
    }

    // --- Evict (gọi khi profile thay đổi, vd: switch online) --
    public void evict(String email) {
        String key = buildKey(email);
        try {
            redisTemplate.delete(key);
            log.debug("[Redis DEL] driver profile for {}", email);
        } catch (Exception e) {
            log.warn("[Redis ERROR] evict driver profile {}: {}", email, e.getMessage());
        }
    }

    // -------------------------------------------------------
    private String buildKey(String email) {
        return KEY_PREFIX + email;
    }
}
