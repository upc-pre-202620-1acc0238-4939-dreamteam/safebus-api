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
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShiftControllerTest {

    private static final String ACTIVATE_URL = "/api/v1/shifts/activate";
    private static final String ASSIGNMENT_URL = "/api/v1/shifts/me/assignment";
    private static final Instant T1 = Instant.parse("2030-01-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-01-01T16:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired Clock clock;

    Company company;
    Bus bus;
    Driver driver;
    Driver otherDriver;
    Route route;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Shift Ctrl Company"));
        bus = busRepository.save(Bus.create(company.getId(), "SHFT-P01", () -> "shft-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 500L, "Shift Driver",
            () -> "SHFT-DRV-QR-001", Duration.ofDays(365), clock));
        otherDriver = driverRepository.save(Driver.create(company.getId(), 501L, "Other Driver",
            () -> "SHFT-DRV-QR-002", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "Shift Route", "From", "To"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor driverJwt(long userAccountId) {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(userAccountId)).claim("role", "DRIVER"))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    private ShiftAssignment assignedShift(Driver d) {
        return assignmentRepository.save(
            ShiftAssignment.create(d.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
    }

    // --- POST /api/v1/shifts/activate ---

    @Test
    void activate_valid_returns201WithResource() throws Exception {
        ShiftAssignment sa = assignedShift(driver);
        String body = String.format("{\"assignmentId\":%d,\"qrCredential\":\"SHFT-DRV-QR-001\"}", sa.getId());

        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.assignmentId").value(sa.getId()))
            .andExpect(jsonPath("$.driverId").value(driver.getId()))
            .andExpect(jsonPath("$.busId").value(bus.getId()))
            .andExpect(jsonPath("$.routeId").value(route.getId()))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.startedAt").isString());
    }

    @Test
    void activate_unknownCredential_returns422CredentialInvalid() throws Exception {
        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":1,\"qrCredential\":\"NONEXISTENT-QR\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CREDENTIAL_INVALID"));
    }

    @Test
    void activate_credentialOfAnotherDriver_returns422CredentialNotOwned() throws Exception {
        // SHFT-DRV-QR-002 belongs to otherDriver (userAccountId 501), but JWT is for user 500
        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":1,\"qrCredential\":\"SHFT-DRV-QR-002\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CREDENTIAL_NOT_OWNED"));
    }

    @Test
    void activate_disabledDriver_returns422CredentialDisabled() throws Exception {
        driver.disable();
        driverRepository.save(driver);
        ShiftAssignment sa = assignedShift(driver);

        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"assignmentId\":%d,\"qrCredential\":\"SHFT-DRV-QR-001\"}", sa.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CREDENTIAL_DISABLED"));
    }

    @Test
    void activate_expiredCredential_returns422CredentialExpired() throws Exception {
        Driver expired = driverRepository.save(
            Driver.create(company.getId(), 502L, "Expired Driver",
                () -> "SHFT-DRV-QR-003", Duration.ofDays(-1), clock));
        ShiftAssignment sa = assignmentRepository.save(
            ShiftAssignment.create(expired.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));

        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(502L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"assignmentId\":%d,\"qrCredential\":\"SHFT-DRV-QR-003\"}", sa.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CREDENTIAL_EXPIRED"));
    }

    @Test
    void activate_assignmentNotFound_returns422AssignmentNotFound() throws Exception {
        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":999999,\"qrCredential\":\"SHFT-DRV-QR-001\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    void activate_assignmentOfAnotherDriver_returns422SameBodyAsNotFound() throws Exception {
        ShiftAssignment otherSa = assignedShift(otherDriver);
        String bodyOther = String.format(
            "{\"assignmentId\":%d,\"qrCredential\":\"SHFT-DRV-QR-001\"}", otherSa.getId());
        String bodyMissing =
            "{\"assignmentId\":999999,\"qrCredential\":\"SHFT-DRV-QR-001\"}";

        var fromOther = mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON).content(bodyOther))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
        var fromMissing = mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON).content(bodyMissing))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

        String bodyOtherStr = fromOther.getResponse().getContentAsString();
        String bodyMissingStr = fromMissing.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(bodyMissingStr, bodyOtherStr);
    }

    @Test
    void activate_alreadyActive_returns409AssignmentNotAvailable() throws Exception {
        ShiftAssignment sa = assignedShift(driver);
        String body = String.format("{\"assignmentId\":%d,\"qrCredential\":\"SHFT-DRV-QR-001\"}", sa.getId());

        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_AVAILABLE"));
    }

    @Test
    void activate_alreadyClosed_returns409AssignmentNotAvailable() throws Exception {
        ShiftAssignment sa = assignedShift(driver);
        java.lang.reflect.Field f = ShiftAssignment.class.getDeclaredField("status");
        f.setAccessible(true);
        f.set(sa, com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.CLOSED);
        assignmentRepository.save(sa);
        String body = String.format("{\"assignmentId\":%d,\"qrCredential\":\"SHFT-DRV-QR-001\"}", sa.getId());

        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_AVAILABLE"));
    }

    @Test
    void activate_missingFields_returns422ValidationFailed() throws Exception {
        mockMvc.perform(post(ACTIVATE_URL).with(driverJwt(500L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void activate_supervisorRole_returns403() throws Exception {
        mockMvc.perform(post(ACTIVATE_URL)
                .with(jwt().jwt(b -> b.claim("role", "SUPERVISOR").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":1,\"qrCredential\":\"X\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void activate_passengerRole_returns403() throws Exception {
        mockMvc.perform(post(ACTIVATE_URL)
                .with(jwt().jwt(b -> b.claim("role", "PASSENGER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":1,\"qrCredential\":\"X\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void activate_noToken_returns401() throws Exception {
        mockMvc.perform(post(ACTIVATE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentId\":1,\"qrCredential\":\"X\"}"))
            .andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/shifts/me/assignment ---

    @Test
    void getAssignment_driverWithAssignment_returns200WithFields() throws Exception {
        ShiftAssignment sa = assignedShift(driver);

        mockMvc.perform(get(ASSIGNMENT_URL).with(driverJwt(500L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignmentId").value(sa.getId()))
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.busPlate").value("SHFT-P01"))
            .andExpect(jsonPath("$.routeName").value("Shift Route"))
            .andExpect(jsonPath("$.origin").value("From"))
            .andExpect(jsonPath("$.destination").value("To"))
            .andExpect(jsonPath("$.plannedStart").isString())
            .andExpect(jsonPath("$.plannedEnd").isString());
    }

    @Test
    void getAssignment_noAssignment_returns404() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_URL).with(driverJwt(500L)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    void getAssignment_supervisorRole_returns403() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_URL)
                .with(jwt().jwt(b -> b.claim("role", "SUPERVISOR").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void getAssignment_passengerRole_returns403() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_URL)
                .with(jwt().jwt(b -> b.claim("role", "PASSENGER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void getAssignment_noToken_returns401() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_URL))
            .andExpect(status().isUnauthorized());
    }
}
