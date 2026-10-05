package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FleetContextFacadeCurrentAssignmentTest {

    private static final Instant T_NOW = Instant.parse("2025-07-01T08:00:00Z");
    private static final Clock FIXED = Clock.fixed(T_NOW, ZoneOffset.UTC);

    @Autowired FleetContextFacade facade;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;

    private Company company() {
        return companyRepository.save(Company.create("CA Test Company"));
    }

    private Bus bus(long companyId, String plate, String qr) {
        return busRepository.save(Bus.create(companyId, plate, () -> qr));
    }

    private Driver driver(long companyId, long userAccountId, String qr) {
        return driverRepository.save(Driver.create(companyId, userAccountId, "Driver " + userAccountId,
            () -> qr, Duration.ofDays(365), FIXED));
    }

    private Route route(long companyId, String name) {
        return routeRepository.save(Route.create(companyId, name, "O", "D"));
    }

    private ShiftAssignment assigned(Driver d, Bus b, Route r, Instant start, Instant end) {
        return assignmentRepository.save(
            ShiftAssignment.create(d.getId(), b.getId(), r.getId(), start, end, 1L, FIXED));
    }

    private ShiftAssignment active(Driver d, Bus b, Route r, Instant start, Instant end) {
        ShiftAssignment sa = assigned(d, b, r, start, end);
        sa.activate();
        return assignmentRepository.save(sa);
    }

    @Test
    void currentAssignment_activeBeforeAssigned_returnsActive() {
        Company c = company();
        Bus b = bus(c.getId(), "CA-P01", "ca-qr1");
        Driver d = driver(c.getId(), 201L, "ca-drv-qr1");
        Route r = route(c.getId(), "CA Route 1");

        Instant start = T_NOW.minusSeconds(3600);
        Instant end = T_NOW.plusSeconds(3600);
        ShiftAssignment act = active(d, b, r, start, end);
        assigned(d, b, r, T_NOW.plusSeconds(7200), T_NOW.plusSeconds(14400));

        var result = facade.findCurrentAssignmentForUserAccount(201L, T_NOW);

        assertTrue(result.isPresent());
        assertEquals(act.getId(), result.get().assignmentId());
        assertEquals("ACTIVE", result.get().status());
    }

    @Test
    void currentAssignment_twoAssigned_picksEarliestStart() {
        Company c = company();
        Bus b = bus(c.getId(), "CA-P02", "ca-qr2");
        Driver d = driver(c.getId(), 202L, "ca-drv-qr2");
        Route r = route(c.getId(), "CA Route 2");

        ShiftAssignment later = assigned(d, b, r,
            T_NOW.plusSeconds(7200), T_NOW.plusSeconds(14400));
        ShiftAssignment earlier = assigned(d, b, r,
            T_NOW.plusSeconds(3600), T_NOW.plusSeconds(10800));

        var result = facade.findCurrentAssignmentForUserAccount(202L, T_NOW);

        assertTrue(result.isPresent());
        assertEquals(earlier.getId(), result.get().assignmentId());
    }

    @Test
    void currentAssignment_activeWithExpiredEnd_ignored() {
        Company c = company();
        Bus b = bus(c.getId(), "CA-P03", "ca-qr3");
        Driver d = driver(c.getId(), 203L, "ca-drv-qr3");
        Route r = route(c.getId(), "CA Route 3");

        // Active but plannedEnd already passed
        ShiftAssignment expiredActive = assigned(d, b, r,
            T_NOW.minusSeconds(7200), T_NOW.minusSeconds(1)); // end before T_NOW
        expiredActive.activate();
        assignmentRepository.save(expiredActive);

        var result = facade.findCurrentAssignmentForUserAccount(203L, T_NOW);

        assertTrue(result.isEmpty());
    }

    @Test
    void currentAssignment_closed_ignored() {
        Company c = company();
        Bus b = bus(c.getId(), "CA-P04", "ca-qr4");
        Driver d = driver(c.getId(), 204L, "ca-drv-qr4");
        Route r = route(c.getId(), "CA Route 4");

        ShiftAssignment sa = assigned(d, b, r,
            T_NOW.plusSeconds(3600), T_NOW.plusSeconds(7200));
        var field = getField(ShiftAssignment.class, "status");
        setField(field, sa,
            com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.CLOSED);
        assignmentRepository.save(sa);

        var result = facade.findCurrentAssignmentForUserAccount(204L, T_NOW);

        assertTrue(result.isEmpty());
    }

    @Test
    void currentAssignment_anotherDriversAssignment_ignored() {
        Company c = company();
        Bus b = bus(c.getId(), "CA-P05", "ca-qr5");
        Driver d1 = driver(c.getId(), 205L, "ca-drv-qr5");
        Driver d2 = driver(c.getId(), 206L, "ca-drv-qr6");
        Route r = route(c.getId(), "CA Route 5");

        assigned(d2, b, r, T_NOW.plusSeconds(3600), T_NOW.plusSeconds(7200));

        var result = facade.findCurrentAssignmentForUserAccount(205L, T_NOW);

        assertTrue(result.isEmpty());
    }

    @Test
    void currentAssignment_noAssignment_returnsEmpty() {
        Company c = company();
        driver(c.getId(), 207L, "ca-drv-qr7");

        var result = facade.findCurrentAssignmentForUserAccount(207L, T_NOW);

        assertTrue(result.isEmpty());
    }

    @Test
    void currentAssignment_viewContainsAllFields() {
        Company c = company();
        Bus b = bus(c.getId(), "CA-P06", "ca-qr8");
        Driver d = driver(c.getId(), 208L, "ca-drv-qr8");
        Route r = routeRepository.save(Route.create(c.getId(), "Full Fields Route", "StartCity", "EndCity"));

        Instant start = T_NOW.plusSeconds(3600);
        Instant end = T_NOW.plusSeconds(10800);
        ShiftAssignment sa = assigned(d, b, r, start, end);

        var result = facade.findCurrentAssignmentForUserAccount(208L, T_NOW);

        assertTrue(result.isPresent());
        var view = result.get();
        assertEquals(sa.getId(), view.assignmentId());
        assertEquals("ASSIGNED", view.status());
        assertEquals("CA-P06", view.busPlate());
        assertEquals("Full Fields Route", view.routeName());
        assertEquals("StartCity", view.origin());
        assertEquals("EndCity", view.destination());
        assertEquals(start, view.plannedStart());
        assertEquals(end, view.plannedEnd());
    }

    private java.lang.reflect.Field getField(Class<?> cls, String name) {
        try {
            var f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(java.lang.reflect.Field f, Object target, Object value) {
        try {
            f.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
