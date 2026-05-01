package org.example.steelbikerunbackend.module.driver.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository tương tác với Redis cho location tài xế.
 *
 * <h3>Key schema:</h3>
 * <ul>
 *   <li>{@code driver:location:{driverId}} → HASH chứa toàn bộ thông tin vị trí</li>
 *   <li>{@code h3:drivers:{h3Index}} → SET chứa danh sách driverId trong ô H3</li>
 * </ul>
 *
 * <p>Tại sao dùng 2 key riêng?
 * <ul>
 *   <li>HASH: đọc toàn bộ thông tin 1 driver → O(1)</li>
 *   <li>SET per H3 cell: k-ring search chỉ cần SUNION của 19 cells → O(N drivers)</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DriverLocationRedisRepository {

    // Prefix key cho location hash của từng driver
    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    // Prefix key cho SET driverId trong từng ô H3
    private static final String H3_DRIVERS_KEY_PREFIX = "h3:drivers:";
    // TTL 60s — nếu heartbeat ngừng > 60s thì key tự xóa
    private static final Duration LOCATION_TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lưu / cập nhật vị trí tài xế vào Redis.
     *
     * <p>Nếu ô H3 thay đổi → xóa khỏi ô cũ, thêm vào ô mới.
     * Không bao giờ ghi vào PostgreSQL ở đây (không blocking heartbeat).
     *
     * @param cache dữ liệu vị trí mới nhất
     * @param oldH3Index ô H3 cũ (null nếu là heartbeat đầu tiên)
     */
    public void save(DriverLocationCache cache, String oldH3Index) {
        String locationKey = LOCATION_KEY_PREFIX + cache.getDriverId();
        String newH3Key    = H3_DRIVERS_KEY_PREFIX + cache.getH3Index();

        // Ghi toàn bộ fields vào HASH, đặt lại TTL 60s
        redisTemplate.opsForHash().put(locationKey, "driverId",   cache.getDriverId());
        redisTemplate.opsForHash().put(locationKey, "latitude",   String.valueOf(cache.getLatitude()));
        redisTemplate.opsForHash().put(locationKey, "longitude",  String.valueOf(cache.getLongitude()));
        redisTemplate.opsForHash().put(locationKey, "h3Index",    cache.getH3Index());
        redisTemplate.opsForHash().put(locationKey, "heading",    cache.getHeading() != null ? String.valueOf(cache.getHeading()) : "");
        redisTemplate.opsForHash().put(locationKey, "speed",      cache.getSpeed() != null ? String.valueOf(cache.getSpeed()) : "");
        redisTemplate.opsForHash().put(locationKey, "isOnline",   String.valueOf(cache.isOnline()));
        redisTemplate.opsForHash().put(locationKey, "updatedAt",  cache.getUpdatedAt().toString());
        redisTemplate.expire(locationKey, LOCATION_TTL);

        // Thêm driverId vào SET của ô H3 mới
        redisTemplate.opsForSet().add(newH3Key, cache.getDriverId());
        redisTemplate.expire(newH3Key, LOCATION_TTL);

        // Nếu ô H3 thay đổi → xóa khỏi ô cũ
        if (oldH3Index != null && !oldH3Index.equals(cache.getH3Index())) {
            String oldH3Key = H3_DRIVERS_KEY_PREFIX + oldH3Index;
            redisTemplate.opsForSet().remove(oldH3Key, cache.getDriverId());
            log.debug("Driver [{}] moved from H3 cell [{}] → [{}]",
                    cache.getDriverId(), oldH3Index, cache.getH3Index());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đọc thông tin vị trí của 1 driver từ Redis.
     * Trả về {@link Optional#empty()} nếu key đã expire.
     */
    public Optional<DriverLocationCache> findByDriverId(String driverId) {
        String key = LOCATION_KEY_PREFIX + driverId;
        List<Object> values = redisTemplate.opsForHash().multiGet(key,
                List.of("driverId", "latitude", "longitude", "h3Index",
                        "heading", "speed", "isOnline", "updatedAt"));

        if (values == null || values.get(0) == null) {
            return Optional.empty();
        }

        return Optional.of(parseFromHash(values));
    }

    /**
     * Lấy danh sách tất cả driverId đang online trong một tập H3 cells (k-ring).
     *
     * <p>Dùng SUNION để hợp tất cả SET của các cells cùng lúc → O(N drivers).
     *
     * @param h3Cells danh sách H3 cell index từ k-ring search
     * @return set driverId đang online trong vùng đó
     */
    public Set<Object> findDriverIdsInH3Cells(List<String> h3Cells) {
        if (h3Cells == null || h3Cells.isEmpty()) {
            return Set.of();
        }

        // Chuyển sang key Redis
        String[] keys = h3Cells.stream()
                .map(cell -> H3_DRIVERS_KEY_PREFIX + cell)
                .toArray(String[]::new);

        // SUNION → hợp tất cả SET, trả về tất cả driverId trong vùng
        Set<Object> result = redisTemplate.opsForSet().union(keys[0],
                keys.length > 1 ? List.of(java.util.Arrays.copyOfRange(keys, 1, keys.length)) : List.of());

        return result != null ? result : Set.of();
    }

    /**
     * Lấy danh sách DriverLocationCache cho nhiều driverId cùng lúc.
     * Dùng sau khi có kết quả từ {@link #findDriverIdsInH3Cells}.
     */
    public List<DriverLocationCache> findAllByDriverIds(Set<Object> driverIds) {
        List<DriverLocationCache> result = new ArrayList<>();

        for (Object driverId : driverIds) {
            findByDriverId(driverId.toString()).ifPresent(result::add);
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE (khi driver offline)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xóa location khi driver offline.
     * Gọi khi: {@code PUT /driver/status} với {@code isOnline=false},
     * hoặc khi switch về Customer mode.
     *
     * @param driverId  ID tài xế
     * @param h3Index   ô H3 hiện tại (để xóa khỏi SET)
     */
    public void delete(String driverId, String h3Index) {
        redisTemplate.delete(LOCATION_KEY_PREFIX + driverId);

        if (h3Index != null) {
            redisTemplate.opsForSet().remove(H3_DRIVERS_KEY_PREFIX + h3Index, driverId);
        }

        log.debug("Driver [{}] location removed from Redis (offline)", driverId);
    }

    /**
     * Scan tất cả key {@code driver:location:*} — dùng trong Sync Job.
     *
     * <p><b>Lưu ý:</b> KEYS pattern không dùng trong production (blocking).
     * Đây là acceptable cho MVP với số driver nhỏ (< 1000).
     * Scale sau: dùng Redis SCAN cursor hoặc lưu danh sách driverIds vào 1 SET riêng.
     */
    public Set<String> scanAllLocationKeys() {
        Set<String> keys = redisTemplate.keys(LOCATION_KEY_PREFIX + "*");
        return keys != null ? keys : Set.of();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private DriverLocationCache parseFromHash(List<Object> values) {
        DriverLocationCache cache = new DriverLocationCache();
        cache.setDriverId(str(values.get(0)));
        cache.setLatitude(parseDouble(values.get(1)));
        cache.setLongitude(parseDouble(values.get(2)));
        cache.setH3Index(str(values.get(3)));
        cache.setHeading(parseFloat(values.get(4)));
        cache.setSpeed(parseFloat(values.get(5)));
        cache.setOnline(Boolean.parseBoolean(str(values.get(6))));
        String updatedAtStr = str(values.get(7));
        if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
            try {
                cache.setUpdatedAt(java.time.Instant.parse(updatedAtStr));
            } catch (Exception e) {
                cache.setUpdatedAt(java.time.Instant.now());
            }
        }
        return cache;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private double parseDouble(Object o) {
        try {
            return o != null ? Double.parseDouble(o.toString()) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Float parseFloat(Object o) {
        if (o == null) return null;
        String s = o.toString();
        if (s.isEmpty()) return null;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
