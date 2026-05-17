package org.example.steelbikerunbackend.module.websocket.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed queue lưu toàn bộ các trip đang trong trạng thái tìm tài xế.
 *
 * <p>Key schema:
 * <pre>
 * matching:queue        → Redis Set chứa tất cả tripId đang active
 * matching:state:{id}   → Redis String (JSON) chứa TripMatchingState
 * </pre>
 *
 * <p>TTL mỗi state = 6 phút (global timeout 5 phút + 1 phút buffer).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingQueue {

    private static final String QUEUE_KEY      = "matching:queue";
    private static final String STATE_KEY_PREFIX = "matching:state:";
    private static final Duration STATE_TTL    = Duration.ofMinutes(6);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Enqueue / Dequeue
    // -------------------------------------------------------------------------

    /**
     * Thêm trip mới vào queue (gọi ngay sau khi tạo trip).
     */
    public void enqueue(TripMatchingState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForSet().add(QUEUE_KEY, state.getTripId());
            redisTemplate.opsForValue().set(stateKey(state.getTripId()), json, STATE_TTL);
            log.info("[MatchingQueue] Enqueued trip {}", state.getTripId());
        } catch (Exception e) {
            log.error("[MatchingQueue] Enqueue failed for trip {}: {}", state.getTripId(), e.getMessage());
        }
    }

    /**
     * Xóa trip khỏi queue — gọi khi driver accept hoặc trip CANCELLED.
     */
    public void remove(String tripId) {
        redisTemplate.opsForSet().remove(QUEUE_KEY, tripId);
        redisTemplate.delete(stateKey(tripId));
        log.info("[MatchingQueue] Removed trip {}", tripId);
    }

    /**
     * Lấy tất cả trip ID đang trong queue.
     */
    public List<String> getAllTripIds() {
        Set<String> members = redisTemplate.opsForSet().members(QUEUE_KEY);
        return members == null ? List.of() : new ArrayList<>(members);
    }

    // -------------------------------------------------------------------------
    // State CRUD
    // -------------------------------------------------------------------------

    /**
     * Đọc trạng thái matching của một trip từ Redis.
     */
    public Optional<TripMatchingState> getState(String tripId) {
        try {
            String json = redisTemplate.opsForValue().get(stateKey(tripId));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, TripMatchingState.class));
        } catch (Exception e) {
            log.error("[MatchingQueue] Read state failed for trip {}: {}", tripId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Ghi đè trạng thái matching (sau mỗi round broadcast hoặc khi driver từ chối).
     */
    public void updateState(TripMatchingState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            // Giữ nguyên TTL cũ (ghi đè key, redis sẽ reset TTL về STATE_TTL)
            redisTemplate.opsForValue().set(stateKey(state.getTripId()), json, STATE_TTL);
        } catch (Exception e) {
            log.error("[MatchingQueue] Update state failed for trip {}: {}", state.getTripId(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private String stateKey(String tripId) {
        return STATE_KEY_PREFIX + tripId;
    }
}
