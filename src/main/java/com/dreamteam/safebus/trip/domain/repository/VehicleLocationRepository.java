package com.dreamteam.safebus.trip.domain.repository;

import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VehicleLocationRepository extends JpaRepository<VehicleLocation, Long> {

    Optional<VehicleLocation> findByBusId(Long busId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vl FROM VehicleLocation vl WHERE vl.busId = :busId")
    Optional<VehicleLocation> findByBusIdForUpdate(@Param("busId") Long busId);
}
