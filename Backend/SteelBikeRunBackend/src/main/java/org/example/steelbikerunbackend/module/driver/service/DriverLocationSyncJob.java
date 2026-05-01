package org.example.steelbikerunbackend.module.driver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.entity.DriverLocation;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRedisRepository;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRepository;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Write-Behind Sync Job: flush dữ liệu vị trí từ Redis → PostgreSQL mỗi 30 giây.
 *
 * <h3>Tại sao cần job này?</h3>
 * <ul>
 *   <li>Redis là primary store cho vị trí realtime (cực nhanh, TTL 60s).</li>
 *   <li>PostgreSQL cần giữ lịch sử để phục vụ analytics và surge pricing.</li>
 *   <li>Ghi Postgres ở mỗi heartbeat (3s/lần) sẽ tạo quá nhiều I/O → dùng batch 30s.</li>
 * </ul>
 *
 * <h3>Idempotency:</h3>
 * Dùng UPSERT ({@code save()} với {@code @Column(unique=true)}) để job có thể chạy lại
 * mà không tạo duplicate. Nếu job fail ở giữa chừng, driver_id đã upsert là safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverLocationSyncJob {

    private final DriverLocationRedisRepository redisRepository;
    private final DriverLocationRepository locationRepository;
    private final DriverRepository driverRepository;

    /**
     * Chạy mỗi 30 giây. Scan tất cả key {@code driver:location:*} trong Redis
     * và UPSERT vào bảng {@code driver_locations} trong PostgreSQL.
     */
    @Scheduled(fixedDelayString = "${app.sync.location-interval-ms:30000}")
    @Transactional
    public void syncLocationsToDB() {
        Set<String> keys = redisRepository.scanAllLocationKeys();

        if (keys.isEmpty()) {
            log.debug("[SyncJob] Không có location nào cần sync.");
            return;
        }

        int successCount = 0;
        int skipCount = 0;

        for (String key : keys) {
            // Key format: "driver:location:{driverId}"
            String driverId = key.replace("driver:location:", "");

            try {
                Optional<DriverLocationCache> cacheOpt = redisRepository.findByDriverId(driverId);
                if (cacheOpt.isEmpty()) {
                    // Key đã expire trong khoảng thời gian scan → bỏ qua
                    skipCount++;
                    continue;
                }

                DriverLocationCache cache = cacheOpt.get();

                // Tìm Driver entity trong Postgres
                Optional<Driver> driverOpt = driverRepository.findById(UUID.fromString(driverId));
                if (driverOpt.isEmpty()) {
                    log.warn("[SyncJob] DriverId [{}] có location trong Redis nhưng không tìm thấy trong DB → bỏ qua",
                            driverId);
                    skipCount++;
                    continue;
                }

                Driver driver = driverOpt.get();

                // UPSERT: tìm bản ghi cũ hoặc tạo mới
                DriverLocation location = locationRepository.findByDriverId(driver.getId())
                        .orElse(DriverLocation.builder().driver(driver).build());

                // Cập nhật tất cả fields từ cache
                location.setH3Index(cache.getH3Index());
                location.setLatitude(cache.getLatitude());
                location.setLongitude(cache.getLongitude());
                location.setHeading(cache.getHeading());
                location.setSpeed(cache.getSpeed());
                // updatedAt được set bởi @PreUpdate trong entity

                locationRepository.save(location);
                successCount++;

            } catch (Exception e) {
                // Không để 1 driver lỗi làm fail cả batch
                log.error("[SyncJob] Lỗi khi sync driverId [{}]: {}", driverId, e.getMessage(), e);
            }
        }

        log.info("[SyncJob] Sync hoàn tất: {} success, {} skip/error (total keys: {})",
                successCount, skipCount, keys.size());
    }
}
