package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class StartJourneyWriterImpl implements StartJourneyWriter {

    private final PassengerJourneyRepository journeyRepository;
    private final FleetContextFacade fleetFacade;
    private final TripContextFacade tripFacade;
    private final Clock clock;

    public StartJourneyWriterImpl(PassengerJourneyRepository journeyRepository,
                                   FleetContextFacade fleetFacade,
                                   TripContextFacade tripFacade,
                                   Clock clock) {
        this.journeyRepository = journeyRepository;
        this.fleetFacade = fleetFacade;
        this.tripFacade = tripFacade;
        this.clock = clock;
    }

    @Override
    public StartJourneyResult write(StartJourneyCommand cmd) {
        String qrCode = cmd.busQrCode();
        if (qrCode == null || qrCode.isBlank()) {
            throw new RuleViolationException("BUS_QR_INVALID", "bus QR code is required");
        }

        FleetContextFacade.BusInfo bus = fleetFacade.findBusByQrCode(qrCode.trim())
            .orElseThrow(() -> new RuleViolationException("BUS_QR_INVALID", "bus QR code not found"));

        if (!bus.enabled()) {
            throw new RuleViolationException("BUS_QR_INVALID", "bus is not enabled");
        }

        TripContextFacade.ShiftInfo shift = tripFacade.findActiveShiftByBusId(bus.busId())
            .orElseThrow(() -> new RuleViolationException("BUS_NOT_IN_SERVICE", "bus has no active shift"));

        // If passenger already has an active journey on this same bus+shift, return it (idempotent)
        var existing = journeyRepository.findByUserAccountIdAndStatus(cmd.userAccountId(), JourneyStatus.ACTIVE);
        if (existing.isPresent()) {
            PassengerJourney activeJourney = existing.get();
            if (activeJourney.getBusId().equals(bus.busId())) {
                FleetContextFacade.ServiceInfo si = serviceInfo(bus, shift);
                return new StartJourneyResult(activeJourney.getId(), false,
                    si.plate(), si.companyName(), si.companyValidated(),
                    si.routeName(), si.origin(), si.destination(), si.driverPublicName());
            }
            throw new ConflictException("ACTIVE_JOURNEY_EXISTS",
                "passenger already has an active journey on a different bus");
        }

        PassengerJourney journey = PassengerJourney.start(
            cmd.userAccountId(), bus.busId(), shift.shiftId(), clock);
        journeyRepository.saveAndFlush(journey);
        FleetContextFacade.ServiceInfo si = serviceInfo(bus, shift);
        return new StartJourneyResult(journey.getId(), true,
            si.plate(), si.companyName(), si.companyValidated(),
            si.routeName(), si.origin(), si.destination(), si.driverPublicName());
    }

    private FleetContextFacade.ServiceInfo serviceInfo(
            FleetContextFacade.BusInfo bus, TripContextFacade.ShiftInfo shift) {
        return fleetFacade.describeService(bus.busId(), shift.routeId(), shift.driverId())
            .orElseThrow(() -> new RuleViolationException("BUS_NOT_IN_SERVICE", "service info unavailable"));
    }
}
