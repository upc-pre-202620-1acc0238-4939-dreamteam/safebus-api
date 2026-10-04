package com.dreamteam.safebus.fleet.domain.repository;

import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

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
