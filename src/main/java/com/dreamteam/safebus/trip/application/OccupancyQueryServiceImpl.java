package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.OccupancyAssessment;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class OccupancyQueryServiceImpl implements OccupancyQueryService {

    private final BusOccupancyRepository occupancyRepository;
    private final DriverShiftRepository shiftRepository;
    private final FleetContextFacade fleetFacade;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public OccupancyQueryServiceImpl(BusOccupancyRepository occupancyRepository,
                                     DriverShiftRepository shiftRepository,
                                     FleetContextFacade fleetFacade,
                                     CurrentUserProvider currentUserProvider,
                                     Clock clock) {
        this.occupancyRepository = occupancyRepository;
        this.shiftRepository = shiftRepository;
        this.fleetFacade = fleetFacade;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public OccupancyResult getByBus(GetOccupancyByBusQuery query) {
        Long companyId = currentUserProvider.current().companyId();
        // A foreign bus and a missing bus give the same 403 so bus ids cannot be enumerated
        var bus = fleetFacade.findBusCapacity(query.busId())
            .filter(b -> b.companyId().equals(companyId))
            .orElseThrow(() -> new ForbiddenOperationException("BUS_ACCESS_DENIED", "access denied"));

        Instant now = Instant.now(clock);
        BusOccupancy occupancy = occupancyRepository.findByBusId(query.busId()).orElse(null);
        Long activeShiftId = shiftRepository
            .findTopByBusIdAndStatusOrderByStartedAtDesc(query.busId(), ShiftStatus.ACTIVE)
            .map(DriverShift::getId)
            .orElse(null);
        var assessment = OccupancyAssessment.of(occupancy, activeShiftId, bus.capacity(), now);
        return new OccupancyResult(query.busId(), assessment.status(), assessment.reason(),
            assessment.count(), bus.capacity(), assessment.load(),
            occupancy == null ? null : occupancy.getLastCountAt(),
            occupancy == null ? null : occupancy.getLastHeartbeatAt(), now);
    }
}
