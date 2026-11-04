package com.dreamteam.safebus.trip.domain.repository;

import com.dreamteam.safebus.trip.domain.model.CountingSource;
import com.dreamteam.safebus.trip.domain.model.CountingSourceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CountingSourceRepository extends JpaRepository<CountingSource, Long> {

    Optional<CountingSource> findByKeyHashAndStatus(String keyHash, CountingSourceStatus status);

    boolean existsByBusIdAndStatus(Long busId, CountingSourceStatus status);

    List<CountingSource> findByBusIdOrderByIdAsc(Long busId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CountingSource s WHERE s.id = :id")
    Optional<CountingSource> findByIdForUpdate(@Param("id") Long id);
}
