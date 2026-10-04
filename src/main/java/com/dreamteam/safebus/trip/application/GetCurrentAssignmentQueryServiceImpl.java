package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class GetCurrentAssignmentQueryServiceImpl implements GetCurrentAssignmentQueryService {

    private final FleetContextFacade fleetFacade;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public GetCurrentAssignmentQueryServiceImpl(FleetContextFacade fleetFacade,
                                                 CurrentUserProvider currentUserProvider,
                                                 Clock clock) {
        this.fleetFacade = fleetFacade;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public CurrentAssignmentResult getCurrentAssignment() {
        var user = currentUserProvider.current();
        return fleetFacade.findCurrentAssignmentForUserAccount(user.userId(), Instant.now(clock))
            .map(v -> new CurrentAssignmentResult(
                v.assignmentId(), v.status(), v.busPlate(), v.routeName(),
                v.origin(), v.destination(), v.plannedStart(), v.plannedEnd()))
            .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND",
                "no current assignment found"));
    }
}
