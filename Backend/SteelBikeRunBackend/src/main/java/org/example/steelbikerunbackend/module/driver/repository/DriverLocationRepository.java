package org.example.steelbikerunbackend.module.driver.repository;

import org.example.steelbikerunbackend.module.driver.entity.DriverLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverLocationRepository extends JpaRepository<DriverLocation, UUID> {

    Optional<DriverLocation> findByDriverId(UUID driverId);

    // Xóa location khi driver offline hẳn (dùng trong cleanup)
    void deleteByDriverId(UUID driverId);

    // Query cho sync job — lấy tất cả location có driver_id nhất định
    @Query("SELECT dl FROM DriverLocation dl JOIN FETCH dl.driver d WHERE d.id = :driverId")
    Optional<DriverLocation> findByDriverIdFetched(UUID driverId);
}
