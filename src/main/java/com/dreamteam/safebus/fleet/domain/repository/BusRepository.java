package com.dreamteam.safebus.fleet.domain.repository;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BusRepository extends JpaRepository<Bus, Long> {

    boolean existsByPlate(String plate);

    Optional<Bus> findByQrCode(String qrCode);

    List<Bus> findByCompanyIdOrderByPlateAsc(Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bus b WHERE b.id = :id")
    Optional<Bus> findByIdForUpdate(@Param("id") Long id);
}
