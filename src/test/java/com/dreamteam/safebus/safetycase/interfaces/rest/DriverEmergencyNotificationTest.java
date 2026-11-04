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
import com.dreamteam.safebus.notification.application.NotificationSubscription;
import com.dreamteam.safebus.notification.application.SubscriptionRegistry;
import com.dreamteam.safebus.notification.support.CapturingSink;
import com.dreamteam.safebus.safetycase.application.SupervisorNotifier;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriverEmergencyNotificationTest {

    private static final String URL = "/api/v1/driver-emergencies";
    private static final long DRIVER_USER_ID = 5600L;
    private static final long FOREIGN_COMPANY = 5699L;
    private static final Instant T1 = Instant.parse("2030-09-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-09-01T16:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository shiftRepository;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired SubscriptionRegistry registry;
    @Autowired SupervisorNotifier notifier;
    @Autowired TransactionTemplate transactions;
    @Autowired ObjectMapper objectMapper;
    @Autowired Clock clock;

    private Company company;
    private Bus bus;
    private Driver driver;
    private Route route;
    private DriverShift shift;
    private final CapturingSink own = new CapturingSink();
    private final CapturingSink foreign = new CapturingSink();
    private final List<NotificationSubscription> subscriptions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("DN Company"));
        bus = busRepository.save(Bus.create(company.getId(), "DN-BUS01", () -> "dn-bus-qr"));
        driver = driverRepository.save(Driver.create(company.getId(), DRIVER_USER_ID, "Secret Driver Name",
            () -> "DN-DRV-QR-SECRET", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "DN Route", "A", "B"));
        ShiftAssignment assignment = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        shift = shiftRepository.save(
            DriverShift.start(assignment.getId(), driver.getId(), bus.getId(), route.getId(), clock));
        subscriptions.add(registry.register(8801L, company.getId(), own));
        subscriptions.add(registry.register(8802L, FOREIGN_COMPANY, foreign));
    }

    @AfterEach
    void tearDown() {
        subscriptions.forEach(registry::remove);
        emergencyRepository.deleteAll();
        shiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private RequestPostProcessor driverJwt() {
        return jwt().jwt(b -> b.subject(String.valueOf(DRIVER_USER_ID)).claim("role", "DRIVER"))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    private String body(String id, long shiftId) {
        return "{\"id\":\"" + id + "\",\"shiftId\":" + shiftId + ",\"activatedAt\":\""
            + Instant.now(clock).minusSeconds(30) + "\",\"latitude\":-12.046,\"longitude\":-77.042}";
    }

    private void submit(String id, long shiftId, int expectedStatus) throws Exception {
        mockMvc.perform(post(URL).with(driverJwt()).contentType(MediaType.APPLICATION_JSON)
                .content(body(id, shiftId)))
            .andExpect(status().is(expectedStatus));
    }

    private Emergency persisted(String id) {
        return emergencyRepository.saveAndFlush(Emergency.activateByDriver(id, company.getId(), driver.getId(),
            bus.getId(), shift.getId(), route.getId(), null, Instant.now(clock).minusSeconds(10), clock));
    }

    @Test
    void createdDriverEmergency_isDeliveredOnceToItsCompanyAsCritical() throws Exception {
        String id = UUID.randomUUID().toString();

        submit(id, shift.getId(), 201);

        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        CapturingSink.Captured event = own.events().getFirst();
        assertEquals("EmergencyActivated", event.name());
        assertEquals(id, event.id());
        JsonNode payload = objectMapper.valueToTree(event.payload());
        assertEquals(id, payload.get("eventId").asString());
        assertEquals("CRITICAL", payload.get("priority").asString());
        assertEquals("DRIVER", payload.get("source").asString());
        assertTrue(payload.get("reference").asString().matches("EM-[0-9A-F]{8}"));
        assertEquals("DN-BUS01", payload.get("bus").get("plate").asString());
        assertEquals(bus.getId(), payload.get("bus").get("busId").asLong());
        assertEquals(shift.getId(), payload.get("shift").get("shiftId").asLong());
        assertEquals(route.getId(), payload.get("shift").get("routeId").asLong());
        assertEquals("DN Route", payload.get("shift").get("routeName").asString());
        assertEquals(-12.046, payload.get("location").get("latitude").asDouble());
        assertEquals(1, own.settledCount(Duration.ofMillis(500)));
    }

    @Test
    void identicalRetry_doesNotNotifyTwice() throws Exception {
        String id = UUID.randomUUID().toString();

        submit(id, shift.getId(), 201);
        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        mockMvc.perform(post(URL).with(driverJwt()).contentType(MediaType.APPLICATION_JSON)
            .content(body(id, shift.getId())));

        assertEquals(1, own.settledCount(Duration.ofMillis(500)));
    }

    @Test
    void rejectedEmergency_notifiesNobody() throws Exception {
        submit(UUID.randomUUID().toString(), shift.getId() + 9999, 403);

        assertEquals(0, own.settledCount(Duration.ofMillis(500)));
    }

    @Test
    void subscriberOfAnotherCompany_receivesNothing() throws Exception {
        submit(UUID.randomUUID().toString(), shift.getId(), 201);

        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        assertEquals(0, foreign.settledCount(Duration.ofMillis(300)));
    }

    @Test
    void payload_carriesNoIdentityPhotoMessageOrInternalIds() throws Exception {
        submit(UUID.randomUUID().toString(), shift.getId(), 201);
        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));

        JsonNode payload = objectMapper.valueToTree(own.events().getFirst().payload());
        String json = payload.toString();

        assertEquals(Set.of("eventId", "type", "priority", "source", "reference", "groupReference", "occurredAt",
            "bus", "shift", "location"), fieldNames(payload));
        assertEquals(Set.of("busId", "plate"), fieldNames(payload.get("bus")));
        assertEquals(Set.of("shiftId", "routeId", "routeName"), fieldNames(payload.get("shift")));
        assertEquals(Set.of("latitude", "longitude"), fieldNames(payload.get("location")));
        assertTrue(payload.get("groupReference").isNull());
        for (String forbidden : List.of("Secret Driver Name", "DN-DRV-QR-SECRET", "dni", "driverId", "companyId",
                "photo", "message", "userId", "supervisor")) {
            assertFalse(json.toLowerCase().contains(forbidden.toLowerCase()), forbidden + " in " + json);
        }
    }

    @Test
    void notifierInsideATransaction_deliversOnlyAfterCommit() throws Exception {
        String id = UUID.randomUUID().toString();

        transactions.executeWithoutResult(status -> {
            persisted(id);
            notifier.driverEmergencyCreated(id);
            assertEquals(0, sleepAndCount(own, 300));
        });

        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        assertEquals(id, own.events().getFirst().id());
    }

    @Test
    void notifierInsideARolledBackTransaction_deliversNothing() throws Exception {
        String id = UUID.randomUUID().toString();

        transactions.executeWithoutResult(status -> {
            persisted(id);
            notifier.driverEmergencyCreated(id);
            status.setRollbackOnly();
        });

        assertEquals(0, own.settledCount(Duration.ofMillis(500)));
        assertTrue(emergencyRepository.findById(id).isEmpty());
    }

    @Test
    void notifierWithoutATransaction_deliversImmediately() throws Exception {
        String id = UUID.randomUUID().toString();
        persisted(id);

        notifier.driverEmergencyCreated(id);

        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
    }

    @Test
    void aFailingSubscriber_doesNotFailTheEmergencyCreation() throws Exception {
        registry.register(8803L, company.getId(), new com.dreamteam.safebus.notification.application.EventSink() {
            @Override
            public void send(String name, String id, Object payload)
                    throws com.dreamteam.safebus.notification.application.SinkClosedException {
                throw new com.dreamteam.safebus.notification.application.SinkClosedException(
                    new java.io.IOException("peer gone"));
            }

            @Override
            public void comment(String text) {
            }

            @Override
            public void close() {
            }
        });

        submit(UUID.randomUUID().toString(), shift.getId(), 201);

        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        assertEquals(1, emergencyRepository.count());
    }

    private static int sleepAndCount(CapturingSink sink, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return sink.events().size();
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
