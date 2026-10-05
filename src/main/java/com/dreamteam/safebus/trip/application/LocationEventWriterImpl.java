package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.domain.model.LocationEvent;
import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.LocationEventRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
// READ_COMMITTED prevents MVCC snapshot gap when acquiring row locks inside this writer
@Transactional(isolation = Isolation.READ_COMMITTED)
public class LocationEventWriterImpl implements LocationEventWriter {

    private final DriverShiftRepository driverShiftRepository;
    private final LocationEventRepository locationEventRepository;
    private final VehicleLocationRepository vehicleLocationRepository;
    private final FleetContextFacade fleetFacade;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public LocationEventWriterImpl(DriverShiftRepository driverShiftRepository,
                                    LocationEventRepository locationEventRepository,
                                    VehicleLocationRepository vehicleLocationRepository,
                                    FleetContextFacade fleetFacade,
                                    CurrentUserProvider currentUserProvider,
                                    Clock clock) {
        this.driverShiftRepository = driverShiftRepository;
        this.locationEventRepository = locationEventRepository;
        this.vehicleLocationRepository = vehicleLocationRepository;
        this.fleetFacade = fleetFacade;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public RecordLocationEventResult write(RecordLocationEventCommand command) {
        // Step 1: reject capturedAt more than 5 minutes in the future (would freeze the current position)
        Instant truncatedCapturedAt = command.capturedAt().truncatedTo(ChronoUnit.MILLIS);
        if (truncatedCapturedAt.isAfter(Instant.now(clock).plus(Duration.ofMinutes(5)))) {
            throw new RuleViolationException("INVALID_CAPTURE_TIME",
                "capturedAt must not be more than 5 minutes in the future");
        }

        // Step 2: lock DriverShift FOR UPDATE before reading data to check ownership
        var shift = driverShiftRepository.findByIdForUpdate(command.shiftId())
            .orElseThrow(() -> new ForbiddenOperationException("LOCATION_SOURCE_NOT_AUTHORIZED",
                "shift not authorized for this driver"));

        Long callerUserId = currentUserProvider.current().userId();
        var driverInfo = fleetFacade.findDriverByUserAccountId(callerUserId)
            .orElseThrow(() -> new ForbiddenOperationException("LOCATION_SOURCE_NOT_AUTHORIZED",
                "shift not authorized for this driver"));

        if (!shift.getDriverId().equals(driverInfo.driverId())) {
            throw new ForbiddenOperationException("LOCATION_SOURCE_NOT_AUTHORIZED",
                "shift not authorized for this driver");
        }

        // Step 3: optional busId must match the shift's bus
        if (command.busId() != null && !command.busId().equals(shift.getBusId())) {
            throw new RuleViolationException("BUS_SHIFT_MISMATCH",
                "busId in the payload does not match the shift's bus");
        }

        Long busId = shift.getBusId();

        // Step 4: duplicate detection (after locking the shift)
        var existingEvent = locationEventRepository.findByEventId(command.eventId());
        if (existingEvent.isPresent()) {
            LocationEvent stored = existingEvent.get();
            if (stored.hasSamePayloadAs(command.shiftId(), command.capturedAt(),
                    command.accuracyMeters(), command.latitude(), command.longitude())) {
                return new RecordLocationEventResult(
                    stored.getEventId(), stored.getShiftId(), stored.getBusId(),
                    stored.getReceivedAt(), stored.isAppliedAsCurrent(), true);
            }
            throw new ConflictException("EVENT_ID_REUSED",
                "an event with this id already exists with a different payload");
        }

        // Step 5: lock VehicleLocation FOR UPDATE and determine if this event updates the position
        VehicleLocation vehicleLocation = vehicleLocationRepository.findByBusIdForUpdate(busId)
            .orElseGet(() -> VehicleLocation.empty(busId));

        LocationEvent event = LocationEvent.create(
            command.eventId(), command.shiftId(), busId,
            command.latitude(), command.longitude(), command.accuracyMeters(),
            truncatedCapturedAt, clock);

        boolean applied = vehicleLocation.applyIfNewer(event, clock);
        if (applied) {
            event.markAppliedAsCurrent();
        }
        vehicleLocationRepository.save(vehicleLocation);

        // saveAndFlush inside the try to catch constraint violations (eventId or busId unique constraints)
        try {
            locationEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            throw ex;  // propagated to RecordLocationEventCommandServiceImpl for retry
        }

        return new RecordLocationEventResult(
            event.getEventId(), event.getShiftId(), event.getBusId(),
            event.getReceivedAt(), event.isAppliedAsCurrent(), false);
    }
}
