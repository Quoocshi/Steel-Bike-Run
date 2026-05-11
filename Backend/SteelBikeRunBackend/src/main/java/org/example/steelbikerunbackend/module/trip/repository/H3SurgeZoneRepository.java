package org.example.steelbikerunbackend.module.trip.repository;

import org.example.steelbikerunbackend.module.trip.entity.H3SurgeZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface H3SurgeZoneRepository extends JpaRepository<H3SurgeZone, String> {

    /**
     * Lấy tất cả surge zones thuộc danh sách ô H3 chỉ định.
     * Dùng khi tính giá: sau khi gridDisk() trả về 19 ô, lấy surge của tất cả.
     */
    @Query("SELECT z FROM H3SurgeZone z WHERE z.h3Index IN :h3Cells")
    List<H3SurgeZone> findAllByH3IndexIn(@Param("h3Cells") List<String> h3Cells);
}
