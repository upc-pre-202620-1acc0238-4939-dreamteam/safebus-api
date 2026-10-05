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
import com.dreamteam.safebus.trip.application.PassengerJourneyAccessPort;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.LocationEventRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleLocationControllerTest {

    private static final String LOCATION_URL_TEMPLATE = "/api/v1/vehicles/%d/location";
    private static final String EVENTS_URL = "/api/v1/location-events";
    private static final Instant T1 = Instant.parse("2030-09-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-09-01T16:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired LocationEventRepository locationEventRepository;
    @Autowired VehicleLocationRepository vehicleLocationRepository;
    @Autowired Clock clock;
    // Use MockitoBean on the port — this is Boot 4's org.springframework.test.context.bean.override.mockito.MockitoBean
    @MockitoBean PassengerJourneyAccessPort passengerJourneyAccessPort;

    Company company;
    Bus bus;
    Driver driver;
    Route route;
    ShiftAssignment assignment;
    DriverShift shift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("VLC Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "VLC-BUS01", () -> "vlc-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 1200L, "VLC Driver",
            () -> "VLC-DRV-QR-001", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "VLC Route", "A", "B"));
        assignment = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        shift = driverShiftRepository.save(
            DriverShift.start(assignment.getId(), driver.getId(), bus.getId(), route.getId(), clock));
        when(passengerJourneyAccessPort.hasActiveJourneyOnBus(any(), any())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        vehicleLocationRepository.deleteAll();
        locationEventRepository.deleteAll();
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor supervisorJwt(long userId,
                                                                                              long companyId) {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(userId))
                .claim("role", "SUPERVISOR")
                .claim("companyId", companyId))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private void postLocation(Instant capturedAt) throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":12.5,\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), capturedAt.toString());
        mockMvc.perform(post(EVENTS_URL)
                .with(jwt().jwt(b -> b.subject("1200").claim("role", "DRIVER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private String locationUrl() {
        return String.format(LOCATION_URL_TEMPLATE, bus.getId());
    }

    // --- US18 S3: expose journey bus position ---

    @Test
    void get_supervisorMatchingCompany_returns200WithFields() throws Exception {
        postLocation(Instant.now(clock).minusSeconds(10));

        mockMvc.perform(get(locationUrl()).with(supervisorJwt(900L, company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.busId").value(bus.getId()))
            .andExpect(jsonPath("$.latitude").value(-12.046))
            .andExpect(jsonPath("$.longitude").value(-77.042))
            .andExpect(jsonPath("$.capturedAt").isString())
            .andExpect(jsonPath("$.accuracyMeters").value(12.5));
    }

    @Test
    void get_noLocationYet_returns404LocationUnavailable() throws Exception {
        mockMvc.perform(get(locationUrl()).with(supervisorJwt(900L, company.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("LOCATION_UNAVAILABLE"));
    }

    @Test
    void get_supervisorForeignBus_returns403BusAccessDenied() throws Exception {
        mockMvc.perform(get(locationUrl())
                .with(supervisorJwt(900L, company.getId() + 999L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));
    }

    @Test
    void get_nonExistentBusId_returns403SameBodyAsForeignBus() throws Exception {
        // Both cases produce identical code and status; instance differs (different URL path)
        mockMvc.perform(get(locationUrl())
                .with(supervisorJwt(900L, company.getId() + 999L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));

        mockMvc.perform(
                get(String.format(LOCATION_URL_TEMPLATE, 999999L))
                    .with(supervisorJwt(900L, company.getId())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"))
            .andExpect(jsonPath("$.detail").value("access denied"));
    }

    @Test
    void get_driverRole_returns403() throws Exception {
        mockMvc.perform(get(locationUrl())
                .with(jwt().jwt(b -> b.claim("role", "DRIVER").subject("1200"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void get_noToken_returns401() throws Exception {
        mockMvc.perform(get(locationUrl()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void get_passengerWithActiveJourney_returns200() throws Exception {
        postLocation(Instant.now(clock).minusSeconds(10));
        when(passengerJourneyAccessPort.hasActiveJourneyOnBus(eq(1300L), eq(bus.getId())))
            .thenReturn(true);

        mockMvc.perform(get(locationUrl())
                .with(jwt().jwt(b -> b.subject("1300").claim("role", "PASSENGER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.busId").value(bus.getId()));
    }

    // --- US18 S4: reject unrelated passenger access ---

    @Test
    void get_passengerNoActiveJourney_returns403BusAccessDenied() throws Exception {
        when(passengerJourneyAccessPort.hasActiveJourneyOnBus(any(), any())).thenReturn(false);

        mockMvc.perform(get(locationUrl())
                .with(jwt().jwt(b -> b.subject("1300").claim("role", "PASSENGER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));
    }
}
