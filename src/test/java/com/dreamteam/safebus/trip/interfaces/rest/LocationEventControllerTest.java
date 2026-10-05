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
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
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

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocationEventControllerTest {

    private static final String URL = "/api/v1/location-events";
    private static final Instant T1 = Instant.parse("2030-08-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-08-01T16:00:00Z");

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

    Company company;
    Bus bus;
    Driver driver;
    Driver otherDriver;
    Route route;
    ShiftAssignment assignment;
    DriverShift shift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("LEC Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "LEC-BUS01", () -> "lec-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 1100L, "LEC Driver",
            () -> "LEC-DRV-QR-001", Duration.ofDays(365), clock));
        otherDriver = driverRepository.save(Driver.create(company.getId(), 1101L, "LEC Other",
            () -> "LEC-DRV-QR-002", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "LEC Route", "A", "B"));
        assignment = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        shift = driverShiftRepository.save(
            DriverShift.start(assignment.getId(), driver.getId(), bus.getId(), route.getId(), clock));
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

    private org.springframework.test.web.servlet.request.RequestPostProcessor driverJwt(long userId) {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(userId)).claim("role", "DRIVER"))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    private String validBody(String eventId, Instant capturedAt) {
        return String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            eventId, shift.getId(), capturedAt.toString());
    }

    // --- US18 S1: 201 on first event, 200 on identical retry ---

    @Test
    void post_validFirstEvent_returns201WithFields() throws Exception {
        String eventId = UUID.randomUUID().toString();
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(eventId, Instant.now(clock).minusSeconds(10))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventId").value(eventId))
            .andExpect(jsonPath("$.shiftId").value(shift.getId()))
            .andExpect(jsonPath("$.busId").value(bus.getId()))
            .andExpect(jsonPath("$.receivedAt").isString())
            .andExpect(jsonPath("$.currentPositionUpdated").value(true));
        assertEquals(1L, locationEventRepository.count());
    }

    @Test
    void post_identicalRetry_returns200SameBodyOneRow() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Instant capturedAt = Instant.now(clock).minusSeconds(10);
        String body = validBody(eventId, capturedAt);

        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        MvcResult second = mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(1L, locationEventRepository.count());
        String secondBody = second.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(secondBody.contains(eventId));
    }

    @Test
    void post_olderAfterNewer_returns201ButPositionUnchanged() throws Exception {
        Instant newer = Instant.now(clock).minusSeconds(5);
        Instant older = Instant.now(clock).minusSeconds(30);

        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(UUID.randomUUID().toString(), newer)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currentPositionUpdated").value(true));

        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(UUID.randomUUID().toString(), older)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currentPositionUpdated").value(false));

        assertEquals(2L, locationEventRepository.count());
        var vl = vehicleLocationRepository.findByBusId(bus.getId()).orElseThrow();
        assertEquals(newer.truncatedTo(java.time.temporal.ChronoUnit.MILLIS), vl.getCapturedAt());
    }

    @Test
    void post_sameEventIdDifferentPayload_returns409EventIdReused() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Instant capturedAt = Instant.now(clock).minusSeconds(10);

        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(eventId, capturedAt)))
            .andExpect(status().isCreated());

        String differentBody = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":99.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            eventId, shift.getId(), capturedAt.toString());

        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(differentBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_ID_REUSED"));
        assertEquals(1L, locationEventRepository.count());
    }

    @Test
    void post_closedShift_accepted() throws Exception {
        Field f = DriverShift.class.getDeclaredField("status");
        f.setAccessible(true);
        f.set(shift, ShiftStatus.CLOSED);
        driverShiftRepository.save(shift);

        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(UUID.randomUUID().toString(), Instant.now(clock).minusSeconds(10))))
            .andExpect(status().isCreated());
        assertEquals(1L, locationEventRepository.count());
    }

    // --- US18 S2: validation and authorization rejections ---

    @Test
    void post_missingEventId_returns422ValidationFailed() throws Exception {
        String body = String.format(
            "{\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_missingShiftId_returns422ValidationFailed() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_missingCapturedAt_returns422ValidationFailed() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId());
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_missingAccuracyMeters_returns422ValidationFailed() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_missingLatitude_returns422ValidationFailed() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_missingLongitude_returns422ValidationFailed() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_invalidEventIdNotUuid_returns422ValidationFailed() throws Exception {
        String body = String.format(
            "{\"eventId\":\"not-a-uuid\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_latitudeTooHigh_returns422InvalidCoordinates() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":90.1,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_latitudeTooLow_returns422InvalidCoordinates() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-90.1,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_longitudeTooHigh_returns422InvalidCoordinates() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":180.1}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_longitudeTooLow_returns422InvalidCoordinates() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-180.1}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_negativeAccuracy_returns422InvalidAccuracy() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":-1.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_ACCURACY"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_capturedAt6MinFuture_returns422InvalidCaptureTime() throws Exception {
        Instant future = Instant.now(clock).plusSeconds(360);
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), future.toString());
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_CAPTURE_TIME"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_busIdMismatch_returns422BusShiftMismatch() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"busId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), bus.getId() + 999L, Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUS_SHIFT_MISMATCH"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_shiftBelongsToAnotherDriver_returns403() throws Exception {
        ShiftAssignment otherAssignment = assignmentRepository.save(
            ShiftAssignment.create(otherDriver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift otherShift = driverShiftRepository.save(
            DriverShift.start(otherAssignment.getId(), otherDriver.getId(), bus.getId(), route.getId(), clock));

        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), otherShift.getId(), Instant.now(clock).minusSeconds(10));
        mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("LOCATION_SOURCE_NOT_AUTHORIZED"));
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_nonExistentShift_returns403SameBodyAsAnotherDriverShift() throws Exception {
        ShiftAssignment otherAssignment = assignmentRepository.save(
            ShiftAssignment.create(otherDriver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift otherShift = driverShiftRepository.save(
            DriverShift.start(otherAssignment.getId(), otherDriver.getId(), bus.getId(), route.getId(), clock));

        Instant capturedAt = Instant.now(clock).minusSeconds(10);

        MvcResult fromAnother = mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
                    UUID.randomUUID(), otherShift.getId(), capturedAt)))
            .andExpect(status().isForbidden())
            .andReturn();

        MvcResult fromMissing = mockMvc.perform(post(URL).with(driverJwt(1100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"eventId\":\"%s\",\"shiftId\":999999,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
                    UUID.randomUUID(), capturedAt)))
            .andExpect(status().isForbidden())
            .andReturn();

        // Both use the same URL (/api/v1/location-events), so instance matches and bodies are byte-identical
        assertEquals(fromMissing.getResponse().getContentAsString(),
                     fromAnother.getResponse().getContentAsString());
        assertEquals(0L, locationEventRepository.count());
        assertEquals(0L, vehicleLocationRepository.count());
    }

    @Test
    void post_supervisorRole_returns403() throws Exception {
        mockMvc.perform(post(URL)
                .with(jwt().jwt(b -> b.claim("role", "SUPERVISOR").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(UUID.randomUUID().toString(), Instant.now(clock).minusSeconds(10))))
            .andExpect(status().isForbidden());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_passengerRole_returns403() throws Exception {
        mockMvc.perform(post(URL)
                .with(jwt().jwt(b -> b.claim("role", "PASSENGER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(UUID.randomUUID().toString(), Instant.now(clock).minusSeconds(10))))
            .andExpect(status().isForbidden());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void post_noToken_returns401() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(UUID.randomUUID().toString(), Instant.now(clock).minusSeconds(10))))
            .andExpect(status().isUnauthorized());
        assertEquals(0L, locationEventRepository.count());
    }
}
