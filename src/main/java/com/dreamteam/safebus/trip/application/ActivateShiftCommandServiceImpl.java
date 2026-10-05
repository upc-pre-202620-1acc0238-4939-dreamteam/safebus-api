package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
// READ_COMMITTED prevents MVCC snapshot gap when acquiring the row lock inside activateAssignment
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ActivateShiftCommandServiceImpl implements ActivateShiftCommandService {

    private final FleetContextFacade fleetFacade;
    private final DriverShiftRepository driverShiftRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ActivateShiftCommandServiceImpl(FleetContextFacade fleetFacade,
                                            DriverShiftRepository driverShiftRepository,
                                            CurrentUserProvider currentUserProvider,
                                            Clock clock) {
        this.fleetFacade = fleetFacade;
        this.driverShiftRepository = driverShiftRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public DriverShift activate(ActivateShiftCommand command) {
        var user = currentUserProvider.current();

        var credentialOpt = fleetFacade.findDriverByQrCredential(command.qrCredential());
        if (credentialOpt.isEmpty()) {
            throw new RuleViolationException("CREDENTIAL_INVALID", "credential is not registered");
        }
        var driverInfo = credentialOpt.get();
        if (!driverInfo.userAccountId().equals(user.userId())) {
            throw new RuleViolationException("CREDENTIAL_NOT_OWNED", "credential does not belong to this driver");
        }
        if (!driverInfo.enabled()) {
            throw new RuleViolationException("CREDENTIAL_DISABLED", "driver account is disabled");
        }
        if (!driverInfo.credentialExpiresAt().isAfter(Instant.now(clock))) {
            throw new RuleViolationException("CREDENTIAL_EXPIRED", "credential has expired");
        }

        var activation = fleetFacade.activateAssignment(command.assignmentId(), driverInfo.driverId());

        DriverShift shift = DriverShift.start(activation.assignmentId(), activation.driverId(),
                                               activation.busId(), activation.routeId(), clock);
        return driverShiftRepository.save(shift);
    }
}
