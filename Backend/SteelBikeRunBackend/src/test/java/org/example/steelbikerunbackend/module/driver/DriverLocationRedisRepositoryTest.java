package org.example.steelbikerunbackend.module.driver;

import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverLocationRedisRepositoryTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private SetOperations<String, Object> setOps;

    private DriverLocationRedisRepository repository;

    private static final String DRIVER_ID = "driver-uuid-1234";
    private static final String H3_INDEX  = "891f1d4b2a3ffff";
    private static final String LOC_KEY   = "driver:location:" + DRIVER_ID;
    private static final String H3_KEY    = "h3:drivers:" + H3_INDEX;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        repository = new DriverLocationRedisRepository(redisTemplate);
    }

    // ─── save ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save: Ghi HASH và thêm vào H3 SET, đặt TTL")
    void save_WritesHashAndSet() {
        DriverLocationCache cache = buildCache(DRIVER_ID, H3_INDEX);

        repository.save(cache, null);

        // Phải ghi đủ 8 fields vào HASH
        verify(hashOps, times(8)).put(eq(LOC_KEY), anyString(), any());
        // Phải đặt TTL cho location key
        verify(redisTemplate).expire(eq(LOC_KEY), any());
        // Phải thêm driverId vào H3 SET
        verify(setOps).add(H3_KEY, DRIVER_ID);
        // Phải đặt TTL cho H3 key
        verify(redisTemplate).expire(eq(H3_KEY), any());
    }

    @Test
    @DisplayName("save: H3 không đổi → KHÔNG xóa ô cũ")
    void save_SameH3_NoRemove() {
        DriverLocationCache cache = buildCache(DRIVER_ID, H3_INDEX);

        repository.save(cache, H3_INDEX); // oldH3 == newH3

        verify(setOps, never()).remove(anyString(), any());
    }

    @Test
    @DisplayName("save: H3 đổi ô → xóa driverId khỏi ô cũ")
    void save_DifferentH3_RemovesFromOldCell() {
        String oldH3    = "891f1d4b000ffff";
        String oldH3Key = "h3:drivers:" + oldH3;
        DriverLocationCache cache = buildCache(DRIVER_ID, H3_INDEX);

        repository.save(cache, oldH3);

        verify(setOps).remove(oldH3Key, DRIVER_ID);
    }

    // ─── findByDriverId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByDriverId: Key tồn tại → parse đúng DriverLocationCache")
    void findByDriverId_Found() {
        Instant now = Instant.now();
        List<Object> values = List.of(
                DRIVER_ID,
                "10.7769",
                "106.7009",
                H3_INDEX,
                "90.0",
                "30.5",
                "true",
                now.toString()
        );
        when(hashOps.multiGet(eq(LOC_KEY), any())).thenReturn(values);

        Optional<DriverLocationCache> result = repository.findByDriverId(DRIVER_ID);

        assertThat(result).isPresent();
        DriverLocationCache cache = result.get();
        assertThat(cache.getDriverId()).isEqualTo(DRIVER_ID);
        assertThat(cache.getLatitude()).isEqualTo(10.7769);
        assertThat(cache.getLongitude()).isEqualTo(106.7009);
        assertThat(cache.getH3Index()).isEqualTo(H3_INDEX);
        assertThat(cache.getHeading()).isEqualTo(90.0f);
        assertThat(cache.getSpeed()).isEqualTo(30.5f);
        assertThat(cache.isOnline()).isTrue();
    }

    @Test
    @DisplayName("findByDriverId: Key không tồn tại (TTL hết) → trả về empty")
    void findByDriverId_NotFound() {
        List<Object> nullValues = new java.util.ArrayList<>();
        nullValues.add(null); // field đầu tiên null = key không tồn tại
        nullValues.add(null);
        nullValues.add(null);
        nullValues.add(null);
        nullValues.add(null);
        nullValues.add(null);
        nullValues.add(null);
        nullValues.add(null);
        when(hashOps.multiGet(eq(LOC_KEY), any())).thenReturn(nullValues);

        Optional<DriverLocationCache> result = repository.findByDriverId(DRIVER_ID);

        assertThat(result).isEmpty();
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: Xóa location key và driverId khỏi H3 SET")
    void delete_RemovesKeyAndSet() {
        repository.delete(DRIVER_ID, H3_INDEX);

        verify(redisTemplate).delete(LOC_KEY);
        verify(setOps).remove(H3_KEY, DRIVER_ID);
    }

    @Test
    @DisplayName("delete: h3Index null → chỉ xóa location key, không gọi SREM")
    void delete_NullH3_SkipsSetRemove() {
        repository.delete(DRIVER_ID, null);

        verify(redisTemplate).delete(LOC_KEY);
        verify(setOps, never()).remove(anyString(), any());
    }

    // ─── scanAllLocationKeys ──────────────────────────────────────────────────

    @Test
    @DisplayName("scanAllLocationKeys: Trả về set keys từ Redis")
    void scanAllLocationKeys_ReturnsKeys() {
        Set<String> expected = Set.of(LOC_KEY, "driver:location:other-id");
        when(redisTemplate.keys("driver:location:*")).thenReturn(expected);

        Set<String> result = repository.scanAllLocationKeys();

        assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("scanAllLocationKeys: Redis trả về null → empty set (không NPE)")
    void scanAllLocationKeys_NullFromRedis_ReturnsEmptySet() {
        when(redisTemplate.keys("driver:location:*")).thenReturn(null);

        Set<String> result = repository.scanAllLocationKeys();

        assertThat(result).isEmpty();
    }

    // ─── HELPER ───────────────────────────────────────────────────────────────

    private DriverLocationCache buildCache(String driverId, String h3Index) {
        return DriverLocationCache.builder()
                .driverId(driverId)
                .latitude(10.7769)
                .longitude(106.7009)
                .h3Index(h3Index)
                .heading(90.0f)
                .speed(30.5f)
                .isOnline(true)
                .updatedAt(Instant.now())
                .build();
    }
}
