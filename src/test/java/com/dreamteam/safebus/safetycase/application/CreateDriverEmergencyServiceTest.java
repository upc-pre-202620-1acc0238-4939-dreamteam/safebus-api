package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CreateDriverEmergencyServiceTest {

    private static final Long   DRIVER_ID   = 10L;
    private static final Long   COMPANY_ID  = 100L;
    private static final Long   USER_ID     = 1000L;
    private static final Long   SHIFT_ID    = 200L;
    private static final Long   BUS_ID      = 300L;
    private static final Long   ROUTE_ID    = 400L;

    @Autowired CreateDriverEmergencyCommandService service;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired Clock clock;

    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean FleetContextFacade  fleetFacade;
    @MockitoBean TripContextFacade   tripFacade;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(USER_ID, "DRIVER", COMPANY_ID));
        when(fleetFacade.findDriverByUserAccountId(USER_ID))
            .thenReturn(Optional.of(new FleetContextFacade.DriverInfo(
                DRIVER_ID, USER_ID, COMPANY_ID, true, null)));
        when(tripFacade.findShiftById(SHIFT_ID))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(
                SHIFT_ID, DRIVER_ID, BUS_ID, ROUTE_ID, "ACTIVE")));
    }

    @AfterEach
    void tearDown() {
        emergencyRepository.deleteAll();
    }

    private Instant recentPast() {
        return Instant.now(clock).minusSeconds(30);
    }

    private CreateDriverEmergencyCommand validCmd() {
        return new CreateDriverEmergencyCommand(
            UUID.randomUUID().toString(), SHIFT_ID, recentPast(), -12.046, -77.042);
    }

    @Test
    void create_valid_returnsDuplicateFalseAndStatusActive() {
        CreateDriverEmergencyResult result = service.create(validCmd());
        assertFalse(result.duplicate());
        assertEquals(EmergencyStatus.ACTIVE.name(), result.status());
        assertEquals(1L, emergencyRepository.count());
    }

    @Test
    void create_invalidActivationTime_throwsRuleViolation() {
        Instant future = Instant.now(clock).plusSeconds(400);
        var cmd = new CreateDriverEmergencyCommand(
            UUID.randomUUID().toString(), SHIFT_ID, future, null, null);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(cmd));
        assertEquals("INVALID_ACTIVATION_TIME", ex.code());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void create_noDriverRecord_throwsShiftNotAuthorized() {
        when(fleetFacade.findDriverByUserAccountId(any())).thenReturn(Optional.empty());

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.create(validCmd()));
        assertEquals("SHIFT_NOT_AUTHORIZED", ex.code());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void create_shiftNotFound_throwsShiftNotAuthorized() {
        when(tripFacade.findShiftById(any())).thenReturn(Optional.empty());

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.create(validCmd()));
        assertEquals("SHIFT_NOT_AUTHORIZED", ex.code());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void create_shiftBelongsToOtherDriver_sameCodeAsNotFound() {
        when(tripFacade.findShiftById(SHIFT_ID))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(
                SHIFT_ID, 99L, BUS_ID, ROUTE_ID, "ACTIVE")));

        ForbiddenOperationException fromOther = assertThrows(ForbiddenOperationException.class,
            () -> service.create(validCmd()));

        when(tripFacade.findShiftById(any())).thenReturn(Optional.empty());
        ForbiddenOperationException fromMissing = assertThrows(ForbiddenOperationException.class,
            () -> service.create(validCmd()));

        assertEquals(fromMissing.code(), fromOther.code());
        assertEquals(fromMissing.getMessage(), fromOther.getMessage());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void create_onlyLatitude_throwsIncompleteCoordinates() {
        var cmd = new CreateDriverEmergencyCommand(
            UUID.randomUUID().toString(), SHIFT_ID, recentPast(), -12.046, null);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(cmd));
        assertEquals("INCOMPLETE_COORDINATES", ex.code());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void create_onlyLongitude_throwsIncompleteCoordinates() {
        var cmd = new CreateDriverEmergencyCommand(
            UUID.randomUUID().toString(), SHIFT_ID, recentPast(), null, -77.042);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(cmd));
        assertEquals("INCOMPLETE_COORDINATES", ex.code());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void create_invalidLatitude_throwsInvalidCoordinates() {
        var cmd = new CreateDriverEmergencyCommand(
            UUID.randomUUID().toString(), SHIFT_ID, recentPast(), 90.1, -77.042);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(cmd));
        assertEquals("INVALID_COORDINATES", ex.code());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void create_withoutCoordinates_persists() {
        var cmd = new CreateDriverEmergencyCommand(
            UUID.randomUUID().toString(), SHIFT_ID, recentPast(), null, null);
        CreateDriverEmergencyResult result = service.create(cmd);
        assertFalse(result.duplicate());
        assertEquals(1L, emergencyRepository.count());
        Emergency stored = emergencyRepository.findById(result.id()).orElseThrow();
        assertNull(stored.getPoint());
    }

    @Test
    void create_identicalRetry_returnsDuplicate() {
        var cmd = validCmd();
        service.create(cmd);

        CreateDriverEmergencyResult second = service.create(cmd);

        assertTrue(second.duplicate());
        assertEquals(1L, emergencyRepository.count());
    }

    @Test
    void create_closedShift_accepted() throws Exception {
        when(tripFacade.findShiftById(SHIFT_ID))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(
                SHIFT_ID, DRIVER_ID, BUS_ID, ROUTE_ID, "CLOSED")));

        CreateDriverEmergencyResult result = service.create(validCmd());
        assertFalse(result.duplicate());
        assertEquals(1L, emergencyRepository.count());
    }
}
