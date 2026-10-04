package com.dreamteam.safebus.fleet.domain.repository;

import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.id = :id")
    Optional<ShiftAssignment> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.driverId = :driverId " +
           "AND sa.plannedStart < :endTime AND :startTime < sa.plannedEnd")
    List<ShiftAssignment> findOverlappingForDriver(
        @Param("driverId") Long driverId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);

    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.busId = :busId " +
           "AND sa.plannedStart < :endTime AND :startTime < sa.plannedEnd")
    List<ShiftAssignment> findOverlappingForBus(
        @Param("busId") Long busId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);
}
