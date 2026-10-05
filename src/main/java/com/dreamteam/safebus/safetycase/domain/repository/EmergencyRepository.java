package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmergencyRepository extends JpaRepository<Emergency, String> {

    List<Emergency> findByBusIdAndShiftIdOrderByReceivedAtDesc(Long busId, Long shiftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Emergency e WHERE e.id = :id")
    Optional<Emergency> findByIdForUpdate(@Param("id") String id);

    List<Emergency> findByCompanyIdAndStatusInOrderByReceivedAtDesc(Long companyId,
                                                                    Collection<EmergencyStatus> statuses);
}
