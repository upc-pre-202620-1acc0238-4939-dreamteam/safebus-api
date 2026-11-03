package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PassengerRequestGroupRepository extends JpaRepository<PassengerRequestGroup, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM PassengerRequestGroup g WHERE g.id = :id")
    Optional<PassengerRequestGroup> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM PassengerRequestGroup g WHERE g.shiftId = :shiftId AND g.openMarker = 1")
    Optional<PassengerRequestGroup> findOpenByShiftIdForUpdate(@Param("shiftId") Long shiftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM PassengerRequestGroup g "
        + "WHERE g.status = com.dreamteam.safebus.safetycase.domain.model.GroupStatus.COLLECTING "
        + "AND g.windowExpiresAt <= :now ORDER BY g.id")
    List<PassengerRequestGroup> findOverdueCollectingForUpdate(@Param("now") Instant now);

    List<PassengerRequestGroup> findByShiftIdOrderByIdAsc(Long shiftId);

    List<PassengerRequestGroup> findByCompanyIdAndStatus(Long companyId, GroupStatus status);
}
