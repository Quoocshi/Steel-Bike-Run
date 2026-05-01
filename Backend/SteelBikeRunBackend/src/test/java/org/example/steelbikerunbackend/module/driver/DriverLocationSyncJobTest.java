package org.example.steelbikerunbackend.module.driver;

import org.example.steelbikerunbackend.module.driver.cache.DriverLocationCache;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.entity.DriverLocation;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRedisRepository;
import org.example.steelbikerunbackend.module.driver.repository.DriverLocationRepository;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationSyncJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverLocationSyncJobTest {

    @Mock private DriverLocationRedisRepository redisRepository;
    @Mock private DriverLocationRepository locationRepository;
    @Mock private DriverRepository driverRepository;

    @InjectMocks
    private DriverLocationSyncJob syncJob;

    private UUID driverId;
    private Driver driver;
    private DriverLocationCache cache;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();

        driver = Driver.builder()
                .id(driverId)
                .vehiclePlate("51G-123.45")
                .isOnline(true)
                .build();

        cache = DriverLocationCache.builder()
                .driverId(driverId.toString())
                .latitude(10.7769)
                .longitude(106.7009)
                .h3Index("891f1d4b2a3ffff")
                .heading(90.0f)
                .speed(30.5f)
                .isOnline(true)
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("syncLocationsToDB: Không có key Redis → không gọi DB")
    void sync_NoKeys_NothingToDo() {
        when(redisRepository.scanAllLocationKeys()).thenReturn(Set.of());

        syncJob.syncLocationsToDB();

        verifyNoInteractions(locationRepository, driverRepository);
    }

    @Test
    @DisplayName("syncLocationsToDB: 1 driver mới (chưa có trong DB) → INSERT")
    void sync_NewDriver_Inserts() {
        String key = "driver:location:" + driverId;
        when(redisRepository.scanAllLocationKeys()).thenReturn(Set.of(key));
        when(redisRepository.findByDriverId(driverId.toString())).thenReturn(Optional.of(cache));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        // Chưa có bản ghi → trả về empty → tạo mới
        when(locationRepository.findByDriverId(driverId)).thenReturn(Optional.empty());
        when(locationRepository.save(any(DriverLocation.class))).thenAnswer(i -> i.getArgument(0));

        syncJob.syncLocationsToDB();

        ArgumentCaptor<DriverLocation> captor = ArgumentCaptor.forClass(DriverLocation.class);
        verify(locationRepository).save(captor.capture());
        DriverLocation saved = captor.getValue();

        assertThat(saved.getDriver()).isEqualTo(driver);
        assertThat(saved.getLatitude()).isEqualTo(10.7769);
        assertThat(saved.getLongitude()).isEqualTo(106.7009);
        assertThat(saved.getH3Index()).isEqualTo("891f1d4b2a3ffff");
        assertThat(saved.getHeading()).isEqualTo(90.0f);
        assertThat(saved.getSpeed()).isEqualTo(30.5f);
    }

    @Test
    @DisplayName("syncLocationsToDB: Driver đã có bản ghi → UPDATE (UPSERT)")
    void sync_ExistingDriver_Updates() {
        String key = "driver:location:" + driverId;
        DriverLocation existingLocation = DriverLocation.builder()
                .driver(driver)
                .h3Index("old_cell")
                .latitude(10.0)
                .longitude(106.0)
                .build();

        when(redisRepository.scanAllLocationKeys()).thenReturn(Set.of(key));
        when(redisRepository.findByDriverId(driverId.toString())).thenReturn(Optional.of(cache));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(locationRepository.findByDriverId(driverId)).thenReturn(Optional.of(existingLocation));
        when(locationRepository.save(any(DriverLocation.class))).thenAnswer(i -> i.getArgument(0));

        syncJob.syncLocationsToDB();

        ArgumentCaptor<DriverLocation> captor = ArgumentCaptor.forClass(DriverLocation.class);
        verify(locationRepository).save(captor.capture());
        // Phải cập nhật đúng giá trị mới từ Redis
        assertThat(captor.getValue().getH3Index()).isEqualTo("891f1d4b2a3ffff");
        assertThat(captor.getValue().getLatitude()).isEqualTo(10.7769);
    }

    @Test
    @DisplayName("syncLocationsToDB: Key Redis expire giữa chừng → bỏ qua, không crash")
    void sync_KeyExpiredMidway_SkipsGracefully() {
        String key = "driver:location:" + driverId;
        when(redisRepository.scanAllLocationKeys()).thenReturn(Set.of(key));
        // Cache đã expire khi đọc
        when(redisRepository.findByDriverId(driverId.toString())).thenReturn(Optional.empty());

        syncJob.syncLocationsToDB();

        // Không gọi DB khi cache không còn
        verifyNoInteractions(locationRepository, driverRepository);
    }

    @Test
    @DisplayName("syncLocationsToDB: Driver không tồn tại trong DB → bỏ qua, không crash")
    void sync_DriverNotInDB_SkipsGracefully() {
        String key = "driver:location:" + driverId;
        when(redisRepository.scanAllLocationKeys()).thenReturn(Set.of(key));
        when(redisRepository.findByDriverId(driverId.toString())).thenReturn(Optional.of(cache));
        when(driverRepository.findById(driverId)).thenReturn(Optional.empty());

        syncJob.syncLocationsToDB();

        verifyNoInteractions(locationRepository);
    }

    @Test
    @DisplayName("syncLocationsToDB: Một driver lỗi → không ảnh hưởng driver khác")
    void sync_OneDriverFails_OthersContinue() {
        UUID driverId2 = UUID.randomUUID();
        Driver driver2 = Driver.builder().id(driverId2).vehiclePlate("51G-999.99").build();
        DriverLocationCache cache2 = DriverLocationCache.builder()
                .driverId(driverId2.toString())
                .latitude(10.8)
                .longitude(106.8)
                .h3Index("891f1d4b333ffff")
                .updatedAt(Instant.now())
                .build();

        String key1 = "driver:location:" + driverId;
        String key2 = "driver:location:" + driverId2;

        when(redisRepository.scanAllLocationKeys()).thenReturn(Set.of(key1, key2));

        // Driver 1: gây exception
        when(redisRepository.findByDriverId(driverId.toString()))
                .thenThrow(new RuntimeException("Redis blip"));

        // Driver 2: thành công
        when(redisRepository.findByDriverId(driverId2.toString())).thenReturn(Optional.of(cache2));
        when(driverRepository.findById(driverId2)).thenReturn(Optional.of(driver2));
        when(locationRepository.findByDriverId(driverId2)).thenReturn(Optional.empty());
        when(locationRepository.save(any(DriverLocation.class))).thenAnswer(i -> i.getArgument(0));

        // Không nín exception ra ngoài
        assertThatCode(() -> syncJob.syncLocationsToDB()).doesNotThrowAnyException();

        // Driver 2 vẫn được save
        verify(locationRepository, times(1)).save(any(DriverLocation.class));
    }
}
