package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyPriority;
import com.dreamteam.safebus.safetycase.domain.model.EmergencySource;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EmergencyWriterTest {

    private static final Long   DRIVER_A    = 10L;
    private static final Long   DRIVER_B    = 11L;
    private static final Long   COMPANY_ID  = 100L;
    private static final Long   USER_A      = 1000L;
    private static final Long   USER_B      = 1001L;
    private static final Long   SHIFT_ID    = 200L;
    private static final Long   BUS_ID      = 300L;
    private static final Long   ROUTE_ID    = 400L;

    @Autowired EmergencyWriter writer;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired Clock clock;

    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean FleetContextFacade  fleetFacade;
    @MockitoBean TripContextFacade   tripFacade;

    @BeforeEach
    void setUpDriverA() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(USER_A, "DRIVER", COMPANY_ID));
        when(fleetFacade.findDriverByUserAccountId(USER_A))
            .thenReturn(Optional.of(new FleetContextFacade.DriverInfo(
                DRIVER_A, USER_A, COMPANY_ID, true, null)));
        when(tripFacade.findShiftById(SHIFT_ID))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(
                SHIFT_ID, DRIVER_A, BUS_ID, ROUTE_ID, "ACTIVE")));
    }

    @AfterEach
    void tearDown() {
        emergencyRepository.deleteAll();
    }

    private Instant recentPast() {
        return Instant.now(clock).minusSeconds(30).truncatedTo(ChronoUnit.MILLIS);
    }

    private CreateDriverEmergencyCommand cmdWith(String id, Instant activatedAt) {
        return new CreateDriverEmergencyCommand(id, SHIFT_ID, activatedAt, -12.046, -77.042);
    }

    // (a) a new emergency is persisted with all expected fields
    @Test
    void write_newEmergency_persistsAllFields() {
        String id = UUID.randomUUID().toString();
        Instant activatedAt = recentPast();

        CreateDriverEmergencyResult result = writer.write(cmdWith(id, activatedAt));

        assertFalse(result.duplicate());
        assertEquals(1L, emergencyRepository.count());

        Emergency stored = emergencyRepository.findById(id).orElseThrow();
        assertEquals(id,                                           stored.getId());
        assertEquals(COMPANY_ID,                                   stored.getCompanyId());
        assertEquals(EmergencySource.DRIVER,                       stored.getSource());
        assertEquals(EmergencyPriority.CRITICAL,                   stored.getPriority());
        assertEquals(EmergencyStatus.ACTIVE,                       stored.getStatus());
        assertEquals(DRIVER_A,                                     stored.getDriverId());
        assertEquals(BUS_ID,                                       stored.getBusId());
        assertEquals(SHIFT_ID,                                     stored.getShiftId());
        assertEquals(ROUTE_ID,                                     stored.getRouteId());
        assertEquals(activatedAt.truncatedTo(ChronoUnit.MILLIS),   stored.getActivatedAt());
        assertNotNull(stored.getReceivedAt());
        assertNotNull(stored.getPoint());
        assertEquals(-12.046, stored.getPoint().getLatitude(),  1e-9);
        assertEquals(-77.042, stored.getPoint().getLongitude(), 1e-9);
        assertNull(stored.getOccupancyCount());
        assertNull(stored.getResponsibleSupervisorUserId());
        assertNull(stored.getAttentionStartedAt());
        assertNull(stored.getClosedAt());
        assertNull(stored.getOutcome());
        assertNull(stored.getUserResponse());
    }

    // (b) same id + different payload → EMERGENCY_ID_REUSED, stored row is field-by-field unchanged
    @Test
    void write_sameIdDifferentPayload_throwsIdReusedAndRowUnchanged() {
        String id = UUID.randomUUID().toString();
        Instant activatedAt = recentPast();

        writer.write(cmdWith(id, activatedAt));
        Emergency before = emergencyRepository.findById(id).orElseThrow();

        // different activatedAt → different payload
        Instant differentTime = activatedAt.minusSeconds(10);
        ConflictException ex = assertThrows(ConflictException.class,
            () -> writer.write(cmdWith(id, differentTime)));
        assertEquals("EMERGENCY_ID_REUSED", ex.code());

        // row must be exactly as created — guards against save() silently merging over it
        Emergency after = emergencyRepository.findById(id).orElseThrow();
        assertEquals(before.getDriverId(),    after.getDriverId());
        assertEquals(before.getShiftId(),     after.getShiftId());
        assertEquals(before.getBusId(),       after.getBusId());
        assertEquals(before.getRouteId(),     after.getRouteId());
        assertEquals(before.getActivatedAt(), after.getActivatedAt());
        assertEquals(before.getReceivedAt(),  after.getReceivedAt());
        assertEquals(before.getStatus(),      after.getStatus());
        assertNotNull(after.getPoint());
        assertEquals(before.getPoint().getLatitude(),  after.getPoint().getLatitude(),  1e-9);
        assertEquals(before.getPoint().getLongitude(), after.getPoint().getLongitude(), 1e-9);
        assertEquals(1L, emergencyRepository.count());
    }

    // (c) same id from another driver → EMERGENCY_ID_REUSED, stored row unchanged
    @Test
    void write_sameIdFromAnotherDriver_throwsIdReusedAndRowUnchanged() {
        String id = UUID.randomUUID().toString();
        Instant activatedAt = recentPast();

        // first create succeeds with DRIVER_A
        writer.write(cmdWith(id, activatedAt));
        Emergency before = emergencyRepository.findById(id).orElseThrow();
        assertEquals(DRIVER_A, before.getDriverId());

        // switch mock to return DRIVER_B
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(USER_B, "DRIVER", COMPANY_ID));
        when(fleetFacade.findDriverByUserAccountId(USER_B))
            .thenReturn(Optional.of(new FleetContextFacade.DriverInfo(
                DRIVER_B, USER_B, COMPANY_ID, true, null)));
        when(tripFacade.findShiftById(SHIFT_ID))
            .thenReturn(Optional.of(new TripContextFacade.ShiftInfo(
                SHIFT_ID, DRIVER_B, BUS_ID, ROUTE_ID, "ACTIVE")));

        ConflictException ex = assertThrows(ConflictException.class,
            () -> writer.write(cmdWith(id, activatedAt)));
        assertEquals("EMERGENCY_ID_REUSED", ex.code());

        Emergency after = emergencyRepository.findById(id).orElseThrow();
        assertEquals(DRIVER_A, after.getDriverId());
        assertEquals(before.getActivatedAt(), after.getActivatedAt());
        assertEquals(before.getStatus(),      after.getStatus());
        assertEquals(1L, emergencyRepository.count());
    }
}
