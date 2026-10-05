package com.dreamteam.safebus.trip.interfaces.rest;

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
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.LocationEventRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Fixtures for the US05 REST tests: one company, one bus, an ACTIVE assignment and an ACTIVE shift per driver. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractShiftCloseRestTest {

    protected static final long DRIVER_USER = 9401L;
    protected static final long OTHER_DRIVER_USER = 9402L;
    protected static final long PASSENGER_USER = 9501L;
    protected static final long OTHER_PASSENGER_USER = 9502L;
    protected static final String BUS_QR = "SCL-BUS-QR";
    protected static final Instant T1 = Instant.parse("2030-07-01T08:00:00Z");
    protected static final Instant T2 = Instant.parse("2030-07-01T16:00:00Z");

    @Autowired protected MockMvc mockMvc;
    @Autowired protected CompanyRepository companyRepository;
    @Autowired protected BusRepository busRepository;
    @Autowired protected DriverRepository driverRepository;
    @Autowired protected RouteRepository routeRepository;
    @Autowired protected ShiftAssignmentRepository assignmentRepository;
    @Autowired protected DriverShiftRepository driverShiftRepository;
    @Autowired protected LocationEventRepository locationEventRepository;
    @Autowired protected VehicleLocationRepository vehicleLocationRepository;
    @Autowired protected PassengerJourneyRepository journeyRepository;
    @Autowired protected EmergencyRepository emergencyRepository;
    @Autowired protected Clock clock;

    protected Company company;
    protected Bus bus;
    protected Bus otherBus;
    protected Route route;
    protected Driver driver;
    protected Driver otherDriver;
    protected DriverShift shift;
    protected DriverShift otherShift;

    @BeforeEach
    void setUpFixtures() {
        company = companyRepository.save(Company.create("SCL Company"));
        bus = busRepository.save(Bus.create(company.getId(), "SCL-BUS-1", () -> BUS_QR));
        otherBus = busRepository.save(Bus.create(company.getId(), "SCL-BUS-2", () -> "SCL-BUS-QR-2"));
        route = routeRepository.save(Route.create(company.getId(), "SCL Route", "A", "B"));
        driver = driverRepository.save(Driver.create(company.getId(), DRIVER_USER, "SCL Driver",
            () -> "SCL-DRV-QR-1", Duration.ofDays(365), clock));
        otherDriver = driverRepository.save(Driver.create(company.getId(), OTHER_DRIVER_USER, "SCL Other Driver",
            () -> "SCL-DRV-QR-2", Duration.ofDays(365), clock));
        shift = activeShift(driver, bus);
        otherShift = activeShift(otherDriver, otherBus);
    }

    @AfterEach
    void tearDownFixtures() {
        emergencyRepository.deleteAll();
        journeyRepository.deleteAll();
        vehicleLocationRepository.deleteAll();
        locationEventRepository.deleteAll();
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.deleteAll();
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    protected DriverShift activeShift(Driver d, Bus b) {
        ShiftAssignment sa = ShiftAssignment.create(d.getId(), b.getId(), route.getId(), T1, T2, 1L, clock);
        sa.activate();
        sa = assignmentRepository.save(sa);
        return driverShiftRepository.save(
            DriverShift.start(sa.getId(), d.getId(), b.getId(), route.getId(), clock));
    }

    protected String closeUrl(long shiftId) {
        return "/api/v1/shifts/" + shiftId + "/close";
    }

    protected RequestPostProcessor driverJwt(long userId) {
        return jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "DRIVER"))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    protected RequestPostProcessor passengerJwt(long userId) {
        return jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    protected RequestPostProcessor supervisorJwt() {
        return jwt().jwt(b -> b.subject("9601").claim("role", "SUPERVISOR").claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }
}
