package com.dreamteam.safebus.trip.domain.repository;

import com.dreamteam.safebus.trip.domain.model.LocationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationEventRepository extends JpaRepository<LocationEvent, Long> {
    Optional<LocationEvent> findByEventId(String eventId);
}
