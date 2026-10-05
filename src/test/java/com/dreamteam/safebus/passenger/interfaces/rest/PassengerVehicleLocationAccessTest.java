package com.dreamteam.safebus.passenger.interfaces.rest;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/vehicles/{id}/location for passengers, using the real PassengerJourneyAccessPort adapter. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PassengerVehicleLocationAccessTest {

    private static final Long PASSENGER_USER_ID = 7001L;
    private static final Instant T1 = Instant.parse("2030-02-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-02-01T20:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired LocationEventRepository locationEventRepository;
    @Autowired VehicleLocationRepository vehicleLocationRepository;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired Clock clock;

    Company company;
    Bus busA;
    Bus busB;
    Driver driverA;
    Driver driverB;
    Route route;
    ShiftAssignment assignmentA;
    ShiftAssignment assignmentB;
    DriverShift shiftA;
    DriverShift shiftB;

    @BeforeEach
    void setUp() throws Exception {
        company = companyRepository.save(Company.create("PVLA Co"));
        busA = busRepository.save(Bus.create(company.getId(), "PVLA-BUS-A", () -> "PVLA-QR-A"));
        busB = busRepository.save(Bus.create(company.getId(), "PVLA-BUS-B", () -> "PVLA-QR-B"));
        driverA = driverRepository.save(Driver.create(company.getId(), 7101L, "PVLA Driver A",
            () -> "PVLA-DRV-A", Duration.ofDays(365), clock));
        driverB = driverRepository.save(Driver.create(company.getId(), 7102L, "PVLA Driver B",
            () -> "PVLA-DRV-B", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "PVLA Route", "A", "B"));
        assignmentA = assignmentRepository.save(
            ShiftAssignment.create(driverA.getId(), busA.getId(), route.getId(), T1, T2, 1L, clock));
        assignmentB = assignmentRepository.save(
            ShiftAssignment.create(driverB.getId(), busB.getId(), route.getId(), T1, T2, 1L, clock));
        shiftA = driverShiftRepository.save(
            DriverShift.start(assignmentA.getId(), driverA.getId(), busA.getId(), route.getId(), clock));
        shiftB = driverShiftRepository.save(
            DriverShift.start(assignmentB.getId(), driverB.getId(), busB.getId(), route.getId(), clock));
        postLocation(7101L, shiftA);
    }

    @AfterEach
    void tearDown() {
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

    private RequestPostProcessor passengerJwt() {
        return jwt().jwt(b -> b.subject(String.valueOf(PASSENGER_USER_ID)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    private void postLocation(long driverUserId, DriverShift shift) throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,"
                + "\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(5));
        mockMvc.perform(post("/api/v1/location-events")
                .with(jwt().jwt(b -> b.subject(String.valueOf(driverUserId)).claim("role", "DRIVER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private long startJourney(String qr) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/journeys")
                .with(passengerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"" + qr + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"journeyId\":(\\d+)")
            .matcher(r.getResponse().getContentAsString());
        m.find();
        return Long.parseLong(m.group(1));
    }

    private String locationUrl(Bus bus) {
        return "/api/v1/vehicles/" + bus.getId() + "/location";
    }

    @Test
    void get_passengerWithActiveJourneyOnThatBus_returns200() throws Exception {
        startJourney("PVLA-QR-A");

        mockMvc.perform(get(locationUrl(busA)).with(passengerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.busId").value(busA.getId()));
    }

    @Test
    void get_passengerAfterJourneyEnds_returns403BusAccessDenied() throws Exception {
        long journeyId = startJourney("PVLA-QR-A");
        mockMvc.perform(get(locationUrl(busA)).with(passengerJwt())).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/journeys/" + journeyId + "/end").with(passengerJwt()))
            .andExpect(status().isOk());

        mockMvc.perform(get(locationUrl(busA)).with(passengerJwt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));
    }

    @Test
    void get_passengerWithJourneyOnAnotherBus_returns403BusAccessDenied() throws Exception {
        startJourney("PVLA-QR-B");

        mockMvc.perform(get(locationUrl(busA)).with(passengerJwt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));
    }
}
