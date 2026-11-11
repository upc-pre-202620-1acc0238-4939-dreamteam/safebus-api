package com.dreamteam.safebus.trip.domain.repository;

import com.dreamteam.safebus.trip.domain.model.OccupancyEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OccupancyEventRepository extends JpaRepository<OccupancyEvent, String> {

    Optional<OccupancyEvent> findByBusIdAndEpochAndSequence(Long busId, int epoch, long sequence);
}
