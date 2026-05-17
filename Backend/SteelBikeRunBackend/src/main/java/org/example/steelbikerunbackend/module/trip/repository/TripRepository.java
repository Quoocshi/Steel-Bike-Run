package org.example.steelbikerunbackend.module.trip.repository;

import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    /**
     * Lịch sử trip của customer — mới nhất trước, dùng cho màn hình "Lịch sử".
     */
    @Query("SELECT t FROM Trip t WHERE t.customer.id = :customerId ORDER BY t.requestedAt DESC")
    List<Trip> findByCustomerIdOrderByRequestedAtDesc(@Param("customerId") UUID customerId);

    /**
     * Lịch sử trip của driver — mới nhất trước.
     */
    @Query("SELECT t FROM Trip t WHERE t.driver.id = :driverId ORDER BY t.requestedAt DESC")
    List<Trip> findByDriverIdOrderByRequestedAtDesc(@Param("driverId") UUID driverId);

    /**
     * Đếm số trip đang REQUESTED trong một ô H3 — dùng để tính pending_trips cho surge.
     */
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.pickupH3Index = :h3Index AND t.status = :status")
    long countByPickupH3IndexAndStatus(
            @Param("h3Index") String h3Index,
            @Param("status") TripStatus status);

    /**
     * Lấy Trip kèm eager-load Customer — dùng trong MatchingEngine (@Scheduled thread)
     * để tránh LazyInitializationException khi access trip.getCustomer().
     */
    @Query("SELECT t FROM Trip t JOIN FETCH t.customer WHERE t.id = :id")
    java.util.Optional<Trip> findByIdWithCustomer(@Param("id") UUID id);
}
