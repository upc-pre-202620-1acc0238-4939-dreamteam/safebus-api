package com.dreamteam.safebus.safetycase.interfaces.rest;

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
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyPriority;
import com.dreamteam.safebus.safetycase.domain.model.EmergencySource;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriverEmergencyControllerTest {

    private static final String POST_URL = "/api/v1/driver-emergencies";
    private static final long   DRIVER_USER_ID       = 5000L;
    private static final long   OTHER_DRIVER_USER_ID = 5001L;
    private static final Instant T1 = Instant.parse("2030-09-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-09-01T16:00:00Z");

    @Autowired MockMvc               mockMvc;
    @Autowired CompanyRepository     companyRepository;
    @Autowired BusRepository         busRepository;
    @Autowired DriverRepository      driverRepository;
    @Autowired RouteRepository       routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired EmergencyRepository   emergencyRepository;
    @Autowired Clock clock;

    Company      company;
    Bus          bus;
    Driver       driver;
    Driver       otherDriver;
    Route        route;
    DriverShift  shift;

    @BeforeEach
    void setUp() {
        company     = companyRepository.save(Company.create("DE Test Company"));
        bus         = busRepository.save(Bus.create(company.getId(), "DE-BUS01", () -> "de-bus-qr1"));
        driver      = driverRepository.save(Driver.create(company.getId(), DRIVER_USER_ID,
                          "DE Driver", () -> "DE-DRV-QR-001", Duration.ofDays(365), clock));
        otherDriver = driverRepository.save(Driver.create(company.getId(), OTHER_DRIVER_USER_ID,
                          "DE Other",  () -> "DE-DRV-QR-002", Duration.ofDays(365), clock));
        route       = routeRepository.save(Route.create(company.getId(), "DE Route", "A", "B"));
        ShiftAssignment sa = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        shift = driverShiftRepository.save(
            DriverShift.start(sa.getId(), driver.getId(), bus.getId(), route.getId(), clock));
    }

    @AfterEach
    void tearDown() {
        emergencyRepository.deleteAll();
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

    private String body(String id, Instant activatedAt, Double lat, Double lon) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(id).append("\"");
        sb.append(",\"shiftId\":").append(shift.getId());
        sb.append(",\"activatedAt\":\"").append(activatedAt).append("\"");
        if (lat != null) sb.append(",\"latitude\":").append(lat);
        if (lon != null) sb.append(",\"longitude\":").append(lon);
        sb.append("}");
        return sb.toString();
    }

    private String validBody() {
        return body(UUID.randomUUID().toString(),
            Instant.now(clock).minusSeconds(30), -12.046, -77.042);
    }

    private String bodyWith(String id) {
        return body(id, Instant.now(clock).minusSeconds(30), -12.046, -77.042);
    }

    // --- US04 S1: driver with active shift → 201, Critical, Active, correct fields ---

    @Test
    void post_validRequest_returns201WithCriticalActive() throws Exception {
        String id = UUID.randomUUID().toString();
        String activatedAt = Instant.now(clock).minusSeconds(30).toString();
        String reqBody = "{\"id\":\"" + id + "\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + activatedAt + "\",\"latitude\":-12.046,\"longitude\":-77.042}";

        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.status").value(EmergencyStatus.ACTIVE.name()))
            .andExpect(jsonPath("$.priority").value(EmergencyPriority.CRITICAL.name()))
            .andExpect(jsonPath("$.activatedAt").isString())
            .andExpect(jsonPath("$.receivedAt").isString());

        assertEquals(1L, emergencyRepository.count());
        Emergency stored = emergencyRepository.findById(id).orElseThrow();
        assertEquals(driver.getId(),       stored.getDriverId());
        assertEquals(bus.getId(),          stored.getBusId());
        assertEquals(route.getId(),        stored.getRouteId());
        assertEquals(EmergencySource.DRIVER, stored.getSource());
        assertEquals(-12.046, stored.getPoint().getLatitude(),  1e-9);
        assertEquals(-77.042, stored.getPoint().getLongitude(), 1e-9);
    }

    @Test
    void post_withoutCoordinates_returns201AndNullLocation() throws Exception {
        String id = UUID.randomUUID().toString();
        String reqBody = "{\"id\":\"" + id + "\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30) + "\"}";

        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isCreated());

        assertEquals(1L, emergencyRepository.count());
        assertNull(emergencyRepository.findById(id).orElseThrow().getPoint());
    }

    // --- US04 S2 backend: old activatedAt → 201 with both timestamps, identical retry → 200 ---

    @Test
    void post_oldActivatedAt_returns201WithBothTimestamps() throws Exception {
        String id = UUID.randomUUID().toString();
        Instant oldActivated = Instant.now(clock).minusSeconds(3600);
        String reqBody = "{\"id\":\"" + id + "\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + oldActivated + "\",\"latitude\":-12.046,\"longitude\":-77.042}";

        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.activatedAt").isString())
            .andExpect(jsonPath("$.receivedAt").isString());

        Emergency stored = emergencyRepository.findById(id).orElseThrow();
        assert !stored.getActivatedAt().equals(stored.getReceivedAt())
            : "activatedAt and receivedAt must differ for offline-queued emergencies";
    }

    @Test
    void post_identicalRetry_returns200SameBodyOneRow() throws Exception {
        String id = UUID.randomUUID().toString();
        Instant activatedAt = Instant.now(clock).minusSeconds(30);
        String reqBody = body(id, activatedAt, -12.046, -77.042);

        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isCreated());

        MvcResult second = mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(1L, emergencyRepository.count());
        assert second.getResponse().getContentAsString().contains(id);
    }

    @Test
    void post_closedShift_accepted() throws Exception {
        Field f = DriverShift.class.getDeclaredField("status");
        f.setAccessible(true);
        f.set(shift, ShiftStatus.CLOSED);
        driverShiftRepository.save(shift);

        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isCreated());
        assertEquals(1L, emergencyRepository.count());
    }

    // --- US04 S4: owner reads Active, In progress, Closed ---

    @Test
    void get_owner_returnsActiveWithoutOutcome() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(bodyWith(id)))
            .andExpect(status().isCreated());

        mockMvc.perform(get(POST_URL + "/" + id).with(driverJwt(DRIVER_USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.status").value(EmergencyStatus.ACTIVE.name()))
            .andExpect(jsonPath("$.activatedAt").isString())
            .andExpect(jsonPath("$.receivedAt").isString())
            .andExpect(jsonPath("$.outcome").doesNotExist());
    }

    @Test
    void get_anotherDriver_returns403() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(bodyWith(id)))
            .andExpect(status().isCreated());

        mockMvc.perform(get(POST_URL + "/" + id).with(driverJwt(OTHER_DRIVER_USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ACCESS_DENIED"));
    }

    @Test
    void get_nonExistentId_sameCodeAsAnotherDriver() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(bodyWith(id)))
            .andExpect(status().isCreated());

        // both must return 403 EMERGENCY_ACCESS_DENIED — instance field differs by design (different URLs)
        mockMvc.perform(get(POST_URL + "/" + id).with(driverJwt(OTHER_DRIVER_USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ACCESS_DENIED"));

        mockMvc.perform(get(POST_URL + "/nonexistent-id").with(driverJwt(DRIVER_USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ACCESS_DENIED"));
    }

    // --- US04 rejections ---

    @Test
    void post_sameIdDifferentPayload_returns409EmergencyIdReused() throws Exception {
        String id = UUID.randomUUID().toString();
        String firstBody = body(id, Instant.now(clock).minusSeconds(30), -12.046, -77.042);
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(firstBody))
            .andExpect(status().isCreated());

        String differentBody = body(id, Instant.now(clock).minusSeconds(60), -12.046, -77.042);
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(differentBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ID_REUSED"));
        assertEquals(1L, emergencyRepository.count());
    }

    @Test
    void post_sameIdFromAnotherDriver_returns409EmergencyIdReused() throws Exception {
        String id = UUID.randomUUID().toString();
        // driver creates it
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(bodyWith(id)))
            .andExpect(status().isCreated());

        // otherDriver has their own shift; use the same id
        ShiftAssignment otherSa = assignmentRepository.save(
            ShiftAssignment.create(otherDriver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift otherShift = driverShiftRepository.save(
            DriverShift.start(otherSa.getId(), otherDriver.getId(), bus.getId(), route.getId(), clock));
        String otherBody = "{\"id\":\"" + id + "\",\"shiftId\":" + otherShift.getId()
            + ",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30) + "\"}";

        mockMvc.perform(post(POST_URL).with(driverJwt(OTHER_DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(otherBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ID_REUSED"));
        assertEquals(1L, emergencyRepository.count());
    }

    @Test
    void post_shiftOfAnotherDriver_returns403ShiftNotAuthorized() throws Exception {
        ShiftAssignment otherSa = assignmentRepository.save(
            ShiftAssignment.create(otherDriver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift otherShift = driverShiftRepository.save(
            DriverShift.start(otherSa.getId(), otherDriver.getId(), bus.getId(), route.getId(), clock));

        String reqBody = "{\"id\":\"" + UUID.randomUUID() + "\",\"shiftId\":" + otherShift.getId()
            + ",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30) + "\"}";

        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SHIFT_NOT_AUTHORIZED"));
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_nonExistentShiftId_sameBodyAsOtherDriverShift() throws Exception {
        ShiftAssignment otherSa = assignmentRepository.save(
            ShiftAssignment.create(otherDriver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift otherShift = driverShiftRepository.save(
            DriverShift.start(otherSa.getId(), otherDriver.getId(), bus.getId(), route.getId(), clock));

        Instant activatedAt = Instant.now(clock).minusSeconds(30);

        MvcResult fromOther = mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"shiftId\":" + otherShift.getId()
                    + ",\"activatedAt\":\"" + activatedAt + "\"}"))
            .andExpect(status().isForbidden()).andReturn();

        MvcResult fromMissing = mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"shiftId\":9999999"
                    + ",\"activatedAt\":\"" + activatedAt + "\"}"))
            .andExpect(status().isForbidden()).andReturn();

        assertEquals(fromOther.getResponse().getContentAsString(),
                     fromMissing.getResponse().getContentAsString());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_onlyLatitude_returns422IncompleteCoordinates() throws Exception {
        String reqBody = "{\"id\":\"" + UUID.randomUUID() + "\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30)
            + "\",\"latitude\":-12.046}";
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INCOMPLETE_COORDINATES"));
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_latitudeTooHigh_returns422InvalidCoordinates() throws Exception {
        String reqBody = "{\"id\":\"" + UUID.randomUUID() + "\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30)
            + "\",\"latitude\":90.1,\"longitude\":-77.042}";
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_activatedAt6MinFuture_returns422InvalidActivationTime() throws Exception {
        Instant future = Instant.now(clock).plusSeconds(400);
        String reqBody = "{\"id\":\"" + UUID.randomUUID() + "\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + future + "\"}";
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_ACTIVATION_TIME"));
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_notUuidId_returns422ValidationFailed() throws Exception {
        String reqBody = "{\"id\":\"not-a-uuid\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30) + "\"}";
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_missingShiftId_returns422ValidationFailed() throws Exception {
        String reqBody = "{\"id\":\"" + UUID.randomUUID()
            + "\",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30) + "\"}";
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_missingActivatedAt_returns422ValidationFailed() throws Exception {
        String reqBody = "{\"id\":\"" + UUID.randomUUID()
            + "\",\"shiftId\":" + shift.getId() + "}";
        mockMvc.perform(post(POST_URL).with(driverJwt(DRIVER_USER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(reqBody))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_supervisorRole_returns403() throws Exception {
        mockMvc.perform(post(POST_URL)
                .with(jwt().jwt(b -> b.claim("role", "SUPERVISOR").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isForbidden());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_passengerRole_returns403() throws Exception {
        mockMvc.perform(post(POST_URL)
                .with(jwt().jwt(b -> b.claim("role", "PASSENGER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER")))
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isForbidden());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void post_noToken_returns401() throws Exception {
        mockMvc.perform(post(POST_URL)
                .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isUnauthorized());
        assertEquals(0L, emergencyRepository.count());
    }

    @Test
    void get_supervisorRole_returns403() throws Exception {
        mockMvc.perform(get(POST_URL + "/any-id")
                .with(jwt().jwt(b -> b.claim("role", "SUPERVISOR").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void get_passengerRole_returns403() throws Exception {
        mockMvc.perform(get(POST_URL + "/any-id")
                .with(jwt().jwt(b -> b.claim("role", "PASSENGER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void get_noToken_returns401() throws Exception {
        mockMvc.perform(get(POST_URL + "/any-id"))
            .andExpect(status().isUnauthorized());
    }

    private void assertNull(Object val) {
        org.junit.jupiter.api.Assertions.assertNull(val);
    }
}
