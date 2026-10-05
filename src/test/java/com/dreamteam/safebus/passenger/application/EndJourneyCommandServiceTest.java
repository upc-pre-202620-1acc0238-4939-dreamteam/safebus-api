package com.dreamteam.safebus.passenger.application;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
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
class EndJourneyCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Long USER_ID = 99L;

    @Mock PassengerJourneyRepository journeyRepository;

    EndJourneyCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EndJourneyCommandServiceImpl(journeyRepository, CLOCK);
    }

    @Test
    void end_manualReason_endsJourney() {
        PassengerJourney journey = PassengerJourney.start(USER_ID, 10L, 20L, CLOCK);
        when(journeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(journey));
        when(journeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EndJourneyResult result = service.end(new EndJourneyCommand(1L, USER_ID, "MANUAL"));

        assertTrue(result.changed());
        assertEquals(JourneyStatus.ENDED, journey.getStatus());
        assertEquals(JourneyEndReason.MANUAL, journey.getEndReason());
    }

    @Test
    void end_signOutReason_endsJourney() {
        PassengerJourney journey = PassengerJourney.start(USER_ID, 10L, 20L, CLOCK);
        when(journeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(journey));
        when(journeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EndJourneyResult result = service.end(new EndJourneyCommand(1L, USER_ID, "SIGN_OUT"));

        assertTrue(result.changed());
        assertEquals(JourneyEndReason.SIGN_OUT, journey.getEndReason());
    }

    @Test
    void end_alreadyEnded_idempotentReturnsFalse() {
        PassengerJourney journey = PassengerJourney.start(USER_ID, 10L, 20L, CLOCK);
        journey.end(JourneyEndReason.MANUAL, Instant.now(CLOCK));
        when(journeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(journey));
        when(journeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EndJourneyResult result = service.end(new EndJourneyCommand(1L, USER_ID, "SIGN_OUT"));

        assertFalse(result.changed());
    }

    @Test
    void end_journeyNotFound_throwsJourneyAccessDenied() {
        when(journeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.end(new EndJourneyCommand(1L, USER_ID, "MANUAL")));
        assertEquals("JOURNEY_ACCESS_DENIED", ex.code());
    }

    @Test
    void end_wrongOwner_throwsJourneyAccessDenied() {
        PassengerJourney journey = PassengerJourney.start(USER_ID, 10L, 20L, CLOCK);
        when(journeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(journey));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.end(new EndJourneyCommand(1L, 999L, "MANUAL")));
        assertEquals("JOURNEY_ACCESS_DENIED", ex.code());
    }

    @Test
    void end_invalidReason_throwsInvalidEndReason() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.end(new EndJourneyCommand(1L, USER_ID, "AUTOMATIC_SEPARATION")));
        assertEquals("INVALID_END_REASON", ex.code());
    }

    @Test
    void end_nullReason_throwsInvalidEndReason() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.end(new EndJourneyCommand(1L, USER_ID, null)));
        assertEquals("INVALID_END_REASON", ex.code());
    }
}
