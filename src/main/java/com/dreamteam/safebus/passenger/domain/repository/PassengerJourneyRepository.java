package com.dreamteam.safebus.passenger.domain.repository;

import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PassengerJourneyRepository extends JpaRepository<PassengerJourney, Long> {

    Optional<PassengerJourney> findByUserAccountIdAndStatus(Long userAccountId, JourneyStatus status);

    boolean existsByUserAccountIdAndBusIdAndStatus(Long userAccountId, Long busId, JourneyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pj FROM PassengerJourney pj WHERE pj.id = :id")
    Optional<PassengerJourney> findByIdForUpdate(@Param("id") Long id);

    // Locked so a passenger ending the same journey at the same time is not overwritten.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PassengerJourney> findByShiftIdAndStatus(Long shiftId, JourneyStatus status);
}
