package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmergencyRepository extends JpaRepository<Emergency, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Emergency e WHERE e.id = :id")
    Optional<Emergency> findByIdForUpdate(@Param("id") String id);
}
