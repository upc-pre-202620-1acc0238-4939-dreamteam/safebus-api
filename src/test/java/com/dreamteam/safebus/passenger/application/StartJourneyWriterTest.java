package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartJourneyWriterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock PassengerJourneyRepository journeyRepository;
    @Mock FleetContextFacade fleetFacade;
    @Mock TripContextFacade tripFacade;

    StartJourneyWriterImpl writer;

    @BeforeEach
    void setUp() {
        writer = new StartJourneyWriterImpl(journeyRepository, fleetFacade, tripFacade, CLOCK);
    }

    @Test
    void start_validQrNoExistingJourney_createsNewJourney() {
        when(fleetFacade.findBusByQrCode("QR-001"))
            .thenReturn(Optional.of(new FleetContextFacade.BusInfo(10L, 1L, true)));
        when(tripFacade.findActiveShiftByBusId(10L))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(20L, 5L, 10L, 3L, "ACTIVE")));
        when(journeyRepository.findByUserAccountIdAndStatus(99L, JourneyStatus.ACTIVE))
            .thenReturn(Optional.empty());
        when(journeyRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fleetFacade.describeService(10L, 3L, 5L)).thenReturn(Optional.of(
            new FleetContextFacade.ServiceInfo("PLATE-1", "Co", true, "Route A", "X", "Y", "Bob")));

        StartJourneyResult result = writer.write(new StartJourneyCommand(99L, "QR-001"));

        assertTrue(result.created());
        assertEquals("PLATE-1", result.plate());
        assertEquals("Co", result.companyName());
        assertEquals("Route A", result.routeName());
        verify(journeyRepository).saveAndFlush(any(PassengerJourney.class));
    }

    @Test
    void start_sameActiveBusSamePassenger_returnsExistingJourneyNotCreated() {
        PassengerJourney existing = PassengerJourney.start(99L, 10L, 20L, CLOCK);
        when(fleetFacade.findBusByQrCode("QR-001"))
            .thenReturn(Optional.of(new FleetContextFacade.BusInfo(10L, 1L, true)));
        when(tripFacade.findActiveShiftByBusId(10L))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(20L, 5L, 10L, 3L, "ACTIVE")));
        when(journeyRepository.findByUserAccountIdAndStatus(99L, JourneyStatus.ACTIVE))
            .thenReturn(Optional.of(existing));
        when(fleetFacade.describeService(10L, 3L, 5L)).thenReturn(Optional.of(
            new FleetContextFacade.ServiceInfo("PLATE-1", "Co", true, "Route A", "X", "Y", "Bob")));

        StartJourneyResult result = writer.write(new StartJourneyCommand(99L, "QR-001"));

        assertFalse(result.created());
        assertEquals("PLATE-1", result.plate());
        assertEquals("Route A", result.routeName());
        verify(journeyRepository, never()).saveAndFlush(any());
    }

    @Test
    void start_differentActiveBus_throwsActiveJourneyExists() {
        PassengerJourney existing = PassengerJourney.start(99L, 99L, 20L, CLOCK); // different busId
        when(fleetFacade.findBusByQrCode("QR-002"))
            .thenReturn(Optional.of(new FleetContextFacade.BusInfo(10L, 1L, true)));
        when(tripFacade.findActiveShiftByBusId(10L))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(20L, 5L, 10L, 3L, "ACTIVE")));
        when(journeyRepository.findByUserAccountIdAndStatus(99L, JourneyStatus.ACTIVE))
            .thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(ConflictException.class,
            () -> writer.write(new StartJourneyCommand(99L, "QR-002")));
        assertEquals("ACTIVE_JOURNEY_EXISTS", ex.code());
    }

    @Test
    void start_unknownQrCode_throwsBusQrInvalid() {
        when(fleetFacade.findBusByQrCode("UNKNOWN")).thenReturn(Optional.empty());

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> writer.write(new StartJourneyCommand(99L, "UNKNOWN")));
        assertEquals("BUS_QR_INVALID", ex.code());
    }

    @Test
    void start_disabledBus_throwsBusQrInvalid() {
        when(fleetFacade.findBusByQrCode("QR-DIS"))
            .thenReturn(Optional.of(new FleetContextFacade.BusInfo(10L, 1L, false)));

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> writer.write(new StartJourneyCommand(99L, "QR-DIS")));
        assertEquals("BUS_QR_INVALID", ex.code());
    }

    @Test
    void start_noActiveShift_throwsBusNotInService() {
        when(fleetFacade.findBusByQrCode("QR-001"))
            .thenReturn(Optional.of(new FleetContextFacade.BusInfo(10L, 1L, true)));
        when(tripFacade.findActiveShiftByBusId(10L)).thenReturn(Optional.empty());

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> writer.write(new StartJourneyCommand(99L, "QR-001")));
        assertEquals("BUS_NOT_IN_SERVICE", ex.code());
    }

    @Test
    void start_nullQrCode_throwsBusQrInvalid() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> writer.write(new StartJourneyCommand(99L, null)));
        assertEquals("BUS_QR_INVALID", ex.code());
    }

    @Test
    void start_blankQrCode_throwsBusQrInvalid() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> writer.write(new StartJourneyCommand(99L, "   ")));
        assertEquals("BUS_QR_INVALID", ex.code());
    }
}
