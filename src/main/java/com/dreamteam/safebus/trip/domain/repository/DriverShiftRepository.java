package com.dreamteam.safebus.trip.domain.repository;

import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DriverShiftRepository extends JpaRepository<DriverShift, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ds FROM DriverShift ds WHERE ds.id = :id")
    Optional<DriverShift> findByIdForUpdate(@Param("id") Long id);

    Optional<DriverShift> findTopByBusIdAndStatusOrderByStartedAtDesc(Long busId, ShiftStatus status);

    List<DriverShift> findByBusIdInAndStatus(Collection<Long> busIds, ShiftStatus status);
}
