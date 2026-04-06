package org.example.steelbikerunbackend.module.driver.repository;

import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByVehiclePlate(String vehiclePlate);

    boolean existsByLicenseNumber(String licenseNumber);

    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.user.id = :userId")
    Optional<Driver> findByUserIdWithUser(@Param("userId") UUID userId);
}
