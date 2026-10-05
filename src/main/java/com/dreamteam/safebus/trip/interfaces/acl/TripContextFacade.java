package com.dreamteam.safebus.trip.interfaces.acl;

import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TripContextFacade {

    public record ShiftInfo(Long shiftId, Long driverId, Long busId, Long routeId, String status) {}

    private final DriverShiftRepository driverShiftRepository;

    public TripContextFacade(DriverShiftRepository driverShiftRepository) {
        this.driverShiftRepository = driverShiftRepository;
    }

    public Optional<ShiftInfo> findShiftById(Long shiftId) {
        return driverShiftRepository.findById(shiftId)
            .map(s -> new ShiftInfo(s.getId(), s.getDriverId(), s.getBusId(),
                                    s.getRouteId(), s.getStatus().name()));
    }

    // returns the latest ACTIVE shift for the bus; a CLOSED shift is never returned
    public Optional<ShiftInfo> findActiveShiftByBusId(Long busId) {
        return driverShiftRepository.findTopByBusIdAndStatusOrderByStartedAtDesc(busId, ShiftStatus.ACTIVE)
            .map(s -> new ShiftInfo(s.getId(), s.getDriverId(), s.getBusId(),
                                    s.getRouteId(), s.getStatus().name()));
    }
}
