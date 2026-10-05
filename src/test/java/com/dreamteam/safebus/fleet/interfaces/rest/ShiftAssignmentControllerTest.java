package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShiftAssignmentControllerTest {

    private static final String URL = "/api/v1/shift-assignments";
    private static final Instant T1 = Instant.parse("2025-06-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2025-06-01T10:00:00Z");
    private static final Instant T3 = Instant.parse("2025-06-01T12:00:00Z");
    private static final Instant T4 = Instant.parse("2025-06-01T14:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(T1, ZoneOffset.UTC);

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;

    Company company1;
    Company company2;
    Bus bus1;
    Bus bus2;
    Driver driver1;
    Driver driver2;
    Route route1;
    Route route2;

    @BeforeEach
    void setUp() {
        company1 = companyRepository.save(Company.create("Ctrl Company One"));
        company2 = companyRepository.save(Company.create("Ctrl Company Two"));

        bus1 = busRepository.save(Bus.create(company1.getId(), "CTL-BUS1", () -> "ctrl-qr1"));
        bus2 = busRepository.save(Bus.create(company2.getId(), "CTL-BUS2", () -> "ctrl-qr2"));

        driver1 = driverRepository.save(Driver.create(company1.getId(), 101L, "Ctrl Driver One",
            () -> "ctrl-cred1", Duration.ofDays(365), FIXED_CLOCK));
        driver2 = driverRepository.save(Driver.create(company2.getId(), 201L, "Ctrl Driver Two",
            () -> "ctrl-cred2", Duration.ofDays(365), FIXED_CLOCK));

        route1 = routeRepository.save(Route.create(company1.getId(), "Ctrl Route One", "A", "B"));
        route2 = routeRepository.save(Route.create(company2.getId(), "Ctrl Route Two", "C", "D"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor supervisor1Jwt() {
        return jwt()
            .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", company1.getId()).subject("42"))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private String body(Long driverId, Long busId, Long routeId, Instant start, Instant end) {
        return String.format(
            "{\"driverId\":%d,\"busId\":%d,\"routeId\":%d,\"plannedStart\":\"%s\",\"plannedEnd\":\"%s\"}",
            driverId, busId, routeId, start, end);
    }

    // US13 S1: valid assignment returns 201 with expected fields
    @Test
    void createShiftAssignment_valid_returns201WithFields() throws Exception {
        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.driverId").value(driver1.getId()))
            .andExpect(jsonPath("$.busId").value(bus1.getId()))
            .andExpect(jsonPath("$.routeId").value(route1.getId()))
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.createdByUserId").value(42))
            .andExpect(jsonPath("$.plannedStart").isString())
            .andExpect(jsonPath("$.plannedEnd").isString())
            .andExpect(jsonPath("$.createdAt").isString());
    }

    // US13 S2: driver overlap returns 409 and count is unchanged
    @Test
    void createShiftAssignment_driverOverlap_returns409() throws Exception {
        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T3)))
            .andExpect(status().isCreated());

        Bus bus1b = busRepository.save(Bus.create(company1.getId(), "CTL-BUS1B", () -> "ctrl-qr1b"));
        long countAfterFirst = assignmentRepository.count();

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1b.getId(), route1.getId(), T2, T4)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_OVERLAP"));

        assertEquals(countAfterFirst, assignmentRepository.count());
    }

    // US13 S2: bus overlap returns 409 and count is unchanged
    @Test
    void createShiftAssignment_busOverlap_returns409() throws Exception {
        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T3)))
            .andExpect(status().isCreated());

        Driver driver1b = driverRepository.save(Driver.create(company1.getId(), 102L, "Ctrl Driver 1b",
            () -> "ctrl-cred1b", Duration.ofDays(365), FIXED_CLOCK));
        long countAfterFirst = assignmentRepository.count();

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1b.getId(), bus1.getId(), route1.getId(), T2, T4)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_OVERLAP"));

        assertEquals(countAfterFirst, assignmentRepository.count());
    }

    // Contiguous periods (end == next start) must succeed
    @Test
    void createShiftAssignment_contiguous_returns201() throws Exception {
        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isCreated());

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T2, T3)))
            .andExpect(status().isCreated());
    }

    // Foreign company and non-existent ID must produce byte-for-byte identical response
    @Test
    void createShiftAssignment_foreignBusAndNonExistentBus_sameResponseBody() throws Exception {
        long beforeCount = assignmentRepository.count();

        String foreignBody = mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus2.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"))
            .andReturn().getResponse().getContentAsString();

        String nonExistentBody = mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), 999999L, route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"))
            .andReturn().getResponse().getContentAsString();

        assertEquals(foreignBody, nonExistentBody);
        assertEquals(beforeCount, assignmentRepository.count());
    }

    // Disabled driver returns 422
    @Test
    void createShiftAssignment_disabledDriver_returns422() throws Exception {
        long before = assignmentRepository.count();
        driver1.disable();
        driverRepository.save(driver1);

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_DISABLED"));

        assertEquals(before, assignmentRepository.count());
    }

    // Disabled bus returns 422
    @Test
    void createShiftAssignment_disabledBus_returns422() throws Exception {
        long before = assignmentRepository.count();
        bus1.disable();
        busRepository.save(bus1);

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_DISABLED"));

        assertEquals(before, assignmentRepository.count());
    }

    // Disabled route returns 422
    @Test
    void createShiftAssignment_disabledRoute_returns422() throws Exception {
        long before = assignmentRepository.count();
        route1.disable();
        routeRepository.save(route1);

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_DISABLED"));

        assertEquals(before, assignmentRepository.count());
    }

    // Invalid period (end before start) returns 422
    @Test
    void createShiftAssignment_invalidPeriod_returns422() throws Exception {
        long before = assignmentRepository.count();

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T2, T1)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

        assertEquals(before, assignmentRepository.count());
    }

    // Missing fields returns 422 VALIDATION_FAILED
    @Test
    void createShiftAssignment_missingFields_returns422ValidationFailed() throws Exception {
        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // DRIVER role returns 403
    @Test
    void createShiftAssignment_driverRole_returns403() throws Exception {
        mockMvc.perform(post(URL)
                .with(jwt()
                    .jwt(b -> b.claim("role", "DRIVER").claim("companyId", company1.getId()).subject("10"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isForbidden());
    }

    // PASSENGER role returns 403
    @Test
    void createShiftAssignment_passengerRole_returns403() throws Exception {
        mockMvc.perform(post(URL)
                .with(jwt()
                    .jwt(b -> b.claim("role", "PASSENGER").subject("20"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isForbidden());
    }

    // No token returns 401
    @Test
    void createShiftAssignment_noToken_returns401() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnauthorized());
    }

    // Company2 supervisor cannot use company1 resources
    @Test
    void createShiftAssignment_company2SupervisorCannotUseCompany1Resources_returns422() throws Exception {
        var supervisor2Jwt = jwt()
            .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", company2.getId()).subject("99"))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));

        long before = assignmentRepository.count();

        mockMvc.perform(post(URL).with(supervisor2Jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"));

        assertEquals(before, assignmentRepository.count());
    }

    // Foreign driver (company2) with own bus and route -> 422 RESOURCE_NOT_IN_COMPANY and count unchanged
    @Test
    void createShiftAssignment_foreignDriver_returns422AndCountUnchanged() throws Exception {
        long before = assignmentRepository.count();

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver2.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"));

        assertEquals(before, assignmentRepository.count());
    }

    // Foreign route (company2) with own bus and driver -> 422 RESOURCE_NOT_IN_COMPANY and count unchanged
    @Test
    void createShiftAssignment_foreignRoute_returns422AndCountUnchanged() throws Exception {
        long before = assignmentRepository.count();

        mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route2.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"));

        assertEquals(before, assignmentRepository.count());
    }

    // Non-existent driver ID must produce byte-for-byte identical response to a foreign driver
    @Test
    void createShiftAssignment_foreignDriverAndNonExistentDriver_sameResponseBody() throws Exception {
        String foreignBody = mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver2.getId(), bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"))
            .andReturn().getResponse().getContentAsString();

        String nonExistentBody = mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(999998L, bus1.getId(), route1.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"))
            .andReturn().getResponse().getContentAsString();

        assertEquals(foreignBody, nonExistentBody);
    }

    // Non-existent route ID must produce byte-for-byte identical response to a foreign route
    @Test
    void createShiftAssignment_foreignRouteAndNonExistentRoute_sameResponseBody() throws Exception {
        String foreignBody = mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), route2.getId(), T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"))
            .andReturn().getResponse().getContentAsString();

        String nonExistentBody = mockMvc.perform(post(URL).with(supervisor1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(driver1.getId(), bus1.getId(), 999997L, T1, T2)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_IN_COMPANY"))
            .andReturn().getResponse().getContentAsString();

        assertEquals(foreignBody, nonExistentBody);
    }
}
