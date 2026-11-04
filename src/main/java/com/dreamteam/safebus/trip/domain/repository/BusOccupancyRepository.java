package com.dreamteam.safebus.trip.domain.repository;

import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusOccupancyRepository extends JpaRepository<BusOccupancy, Long> {

    Optional<BusOccupancy> findByBusId(Long busId);

    List<BusOccupancy> findByBusIdIn(Collection<Long> busIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM BusOccupancy o WHERE o.busId = :busId")
    Optional<BusOccupancy> findByBusIdForUpdate(@Param("busId") Long busId);
}
