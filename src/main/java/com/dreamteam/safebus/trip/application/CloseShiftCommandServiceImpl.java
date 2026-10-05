package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
// READ_COMMITTED so the shift read after the row lock sees the state committed by a concurrent close
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CloseShiftCommandServiceImpl implements CloseShiftCommandService {

    private static final String NOT_AUTHORIZED_MESSAGE = "shift not authorized for this driver";

    private final DriverShiftRepository driverShiftRepository;
    private final VehicleLocationRepository vehicleLocationRepository;
    private final FleetContextFacade fleetFacade;
    private final ShiftJourneyClosurePort shiftJourneyClosurePort;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CloseShiftCommandServiceImpl(DriverShiftRepository driverShiftRepository,
                                        VehicleLocationRepository vehicleLocationRepository,
                                        FleetContextFacade fleetFacade,
                                        ShiftJourneyClosurePort shiftJourneyClosurePort,
                                        CurrentUserProvider currentUserProvider,
                                        Clock clock) {
        this.driverShiftRepository = driverShiftRepository;
        this.vehicleLocationRepository = vehicleLocationRepository;
        this.fleetFacade = fleetFacade;
        this.shiftJourneyClosurePort = shiftJourneyClosurePort;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public DriverShift close(CloseShiftCommand command) {
        DriverShift shift = driverShiftRepository.findByIdForUpdate(command.shiftId())
            .orElseThrow(this::notAuthorized);

        Long callerUserId = currentUserProvider.current().userId();
        var driverInfo = fleetFacade.findDriverByUserAccountId(callerUserId)
            .orElseThrow(this::notAuthorized);
        if (!shift.getDriverId().equals(driverInfo.driverId())) {
            throw notAuthorized();
        }

        if (shift.getStatus() == ShiftStatus.CLOSED) {
            return shift;
        }

        Instant now = Instant.now(clock);
        Optional<VehicleLocation> lastLocation = vehicleLocationRepository.findByBusId(shift.getBusId());
        GeoPoint lastPoint = lastLocation.map(VehicleLocation::getPoint)
            .map(p -> new GeoPoint(p.getLatitude(), p.getLongitude()))
            .orElse(null);
        Instant lastCapturedAt = lastLocation.map(VehicleLocation::getCapturedAt).orElse(null);
        shift.close(now, lastPoint, lastCapturedAt);

        fleetFacade.closeAssignment(shift.getAssignmentId());
        shiftJourneyClosurePort.endJourneysOfShift(shift.getId(), now);

        return driverShiftRepository.saveAndFlush(shift);
    }

    private ForbiddenOperationException notAuthorized() {
        return new ForbiddenOperationException("SHIFT_NOT_AUTHORIZED", NOT_AUTHORIZED_MESSAGE);
    }
}
