package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GetDriverEmergencyQueryServiceTest {

    private static final Long DRIVER_ID  = 10L;
    private static final Long COMPANY_ID = 100L;
    private static final Long USER_ID    = 1000L;

    @Autowired GetDriverEmergencyQueryService service;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired Clock clock;

    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean FleetContextFacade  fleetFacade;

    private Emergency stored;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(USER_ID, "DRIVER", COMPANY_ID));
        when(fleetFacade.findDriverByUserAccountId(USER_ID))
            .thenReturn(Optional.of(new FleetContextFacade.DriverInfo(
                DRIVER_ID, USER_ID, COMPANY_ID, true, null)));

        stored = emergencyRepository.save(
            Emergency.activateByDriver(
                UUID.randomUUID().toString(),
                COMPANY_ID, DRIVER_ID, 300L, 200L, 400L,
                new GeoPoint(-12.046, -77.042),
                Instant.now(clock).minusSeconds(60), clock));
    }

    @AfterEach
    void tearDown() {
        emergencyRepository.deleteAll();
    }

    @Test
    void get_owner_returnsAllFields() {
        GetDriverEmergencyResult result = service.getForDriver(stored.getId());
        assertEquals(stored.getId(),              result.id());
        assertEquals(EmergencyStatus.ACTIVE.name(), result.status());
        assertEquals(stored.getActivatedAt(),     result.activatedAt());
        assertEquals(stored.getReceivedAt(),      result.receivedAt());
        assertNull(result.attentionStartedAt());
        assertNull(result.closedAt());
        assertNull(result.userResponse());
    }

    @Test
    void get_anotherDriver_throwsAccessDenied() {
        when(fleetFacade.findDriverByUserAccountId(USER_ID))
            .thenReturn(Optional.of(new FleetContextFacade.DriverInfo(
                99L, USER_ID, COMPANY_ID, true, null)));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.getForDriver(stored.getId()));
        assertEquals("EMERGENCY_ACCESS_DENIED", ex.code());
    }

    @Test
    void get_nonExistentId_throwsAccessDenied() {
        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.getForDriver("nonexistent-000000000000000000000000000"));
        assertEquals("EMERGENCY_ACCESS_DENIED", ex.code());
    }

    @Test
    void get_anotherDriverAndNonExistent_haveSameMessage() {
        when(fleetFacade.findDriverByUserAccountId(USER_ID))
            .thenReturn(Optional.of(new FleetContextFacade.DriverInfo(
                99L, USER_ID, COMPANY_ID, true, null)));

        ForbiddenOperationException fromOther = assertThrows(ForbiddenOperationException.class,
            () -> service.getForDriver(stored.getId()));
        ForbiddenOperationException fromMissing = assertThrows(ForbiddenOperationException.class,
            () -> service.getForDriver("nonexistent-000000000000000000000000000"));

        assertEquals(fromOther.code(),    fromMissing.code());
        assertEquals(fromOther.getMessage(), fromMissing.getMessage());
    }

    @Test
    void get_noDriverRecord_throwsAccessDenied() {
        when(fleetFacade.findDriverByUserAccountId(USER_ID)).thenReturn(Optional.empty());
        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.getForDriver(stored.getId()));
        assertEquals("EMERGENCY_ACCESS_DENIED", ex.code());
    }
}
