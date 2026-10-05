package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
// READ_COMMITTED prevents MVCC snapshot gap when reading data within this transaction
@Transactional(isolation = Isolation.READ_COMMITTED)
public class EmergencyWriterImpl implements EmergencyWriter {

    private final EmergencyRepository emergencyRepository;
    private final FleetContextFacade fleetFacade;
    private final TripContextFacade tripFacade;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public EmergencyWriterImpl(EmergencyRepository emergencyRepository,
                                FleetContextFacade fleetFacade,
                                TripContextFacade tripFacade,
                                CurrentUserProvider currentUserProvider,
                                Clock clock) {
        this.emergencyRepository = emergencyRepository;
        this.fleetFacade = fleetFacade;
        this.tripFacade = tripFacade;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public CreateDriverEmergencyResult write(CreateDriverEmergencyCommand command) {
        // Step 1: activatedAt more than 5 minutes after now is rejected
        Instant now = Instant.now(clock);
        if (command.activatedAt().truncatedTo(ChronoUnit.MILLIS)
                .isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new RuleViolationException("INVALID_ACTIVATION_TIME",
                "activatedAt must not be more than 5 minutes in the future");
        }

        // Step 2: resolve the calling driver — companyId comes from here, not the request
        Long callerUserId = currentUserProvider.current().userId();
        FleetContextFacade.DriverInfo driverInfo = fleetFacade
            .findDriverByUserAccountId(callerUserId)
            .orElseThrow(() -> new ForbiddenOperationException("SHIFT_NOT_AUTHORIZED",
                "driver record not found for this account"));

        // Step 3: shift must exist and belong to this driver; status is NOT checked
        TripContextFacade.ShiftInfo shiftInfo = tripFacade.findShiftById(command.shiftId())
            .filter(s -> s.driverId().equals(driverInfo.driverId()))
            .orElseThrow(() -> new ForbiddenOperationException("SHIFT_NOT_AUTHORIZED",
                "driver record not found for this account"));

        // Step 4: coordinates must both be present or both absent
        GeoPoint point = null;
        if (command.latitude() != null || command.longitude() != null) {
            if (command.latitude() == null || command.longitude() == null) {
                throw new RuleViolationException("INCOMPLETE_COORDINATES",
                    "latitude and longitude must both be present or both absent");
            }
            point = new GeoPoint(command.latitude(), command.longitude());
        }

        // Step 5: pre-check — an identical retry must return the duplicate on the first attempt
        var existing = emergencyRepository.findById(command.id());
        if (existing.isPresent()) {
            Emergency stored = existing.get();
            if (stored.hasSamePayloadAs(driverInfo.driverId(), command.shiftId(),
                    command.activatedAt(), command.latitude(), command.longitude())) {
                return new CreateDriverEmergencyResult(stored.getId(), stored.getStatus().name(),
                    stored.getPriority().name(), stored.getActivatedAt(), stored.getReceivedAt(), true);
            }
            throw new ConflictException("EMERGENCY_ID_REUSED",
                "an emergency with this id already exists with a different payload");
        }

        // Step 6: persist — busId and routeId come from the shift, companyId from the driver
        Emergency emergency = Emergency.activateByDriver(
            command.id(), driverInfo.companyId(), driverInfo.driverId(),
            shiftInfo.busId(), command.shiftId(), shiftInfo.routeId(),
            point, command.activatedAt(), clock);

        try {
            emergencyRepository.saveAndFlush(emergency);
        } catch (DataIntegrityViolationException ex) {
            throw ex; // propagated to the orchestrator for retry
        }

        return new CreateDriverEmergencyResult(emergency.getId(), emergency.getStatus().name(),
            emergency.getPriority().name(), emergency.getActivatedAt(), emergency.getReceivedAt(), false);
    }
}
