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
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.safetycase.application.PublicReference;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Not @Transactional and on the real clock: writers commit independently and the delivery threads read committed rows.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupervisorNotificationFlowTest {

    static final int CONCURRENCY_ROUNDS = 10;
    private static final long FIRST_USER = 66001L;
    private static final long SUPERVISOR = 66900L;
    private static final long FOREIGN_COMPANY = 66999L;
    private static final String MESSAGE = "Secret panic message";

    @Autowired MockMvc mockMvc;
    @Autowired Clock clock;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired SubscriptionRegistry registry;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository shiftRepository;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired PassengerRequestRepository requestRepository;
    @Autowired PassengerRequestGroupRepository groupRepository;
    @Autowired StoredImageRepository storedImageRepository;

    private Company company;
    private Bus bus;
    private Driver driver;
    private Route route;
    private ShiftAssignment assignment;
    private DriverShift shift;
    private final List<Long> journeyIds = new ArrayList<>();
    private final CapturingSink own = new CapturingSink();
    private final CapturingSink foreign = new CapturingSink();
    private final List<NotificationSubscription> subscriptions = new ArrayList<>();

    @BeforeEach
    void createFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("SNF Company " + suffix));
        bus = busRepository.save(Bus.create(company.getId(), "SNF-" + suffix, () -> "snf-qr-" + suffix));
        driver = driverRepository.save(Driver.create(company.getId(),
            9_300_000L + Math.abs(suffix.hashCode() % 100_000), "SNF Driver", () -> "SNF-DRV-" + suffix,
            Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "SNF Route " + suffix, "A", "B"));
        assignment = assignmentRepository.save(ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(),
            clock.instant().minusSeconds(3600), clock.instant().plusSeconds(3600), 1L, clock));
        shift = shiftRepository.save(DriverShift.start(assignment.getId(), driver.getId(), bus.getId(),
            route.getId(), clock));
        subscriptions.add(registry.register(SUPERVISOR, company.getId(), own));
        subscriptions.add(registry.register(SUPERVISOR + 1, FOREIGN_COMPANY, foreign));
    }

    @AfterEach
    void deleteFixture() {
        subscriptions.forEach(registry::remove);
        clearShiftCases();
        journeyRepository.deleteAllById(journeyIds);
        shiftRepository.delete(shift);
        assignmentRepository.delete(assignment);
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private void clearShiftCases() {
        jdbcTemplate.update("delete from emergency where shift_id = ?", shift.getId());
        List<PassengerRequest> requests = requestRepository.findAll().stream()
            .filter(r -> r.getShiftId().equals(shift.getId())).toList();
        requestRepository.deleteAll(requests);
        storedImageRepository.deleteAllById(requests.stream().map(PassengerRequest::getEvidenceImageId).toList());
        groupRepository.deleteAll(groupRepository.findByShiftIdOrderByIdAsc(shift.getId()));
    }

    private int submit(long userId) throws Exception {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(userId, bus.getId(), shift.getId(), clock));
        synchronized (journeyIds) {
            journeyIds.add(journey.getId());
        }
        return mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg",
                    TestImages.jpeg((int) (userId % 90))))
                .param("id", UUID.randomUUID().toString())
                .param("journeyId", String.valueOf(journey.getId()))
                .param("message", MESSAGE)
                .param("activatedAt", clock.instant().minusSeconds(2).toString())
                .with(jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andReturn().getResponse().getStatus();
    }

    private RequestPostProcessor supervisor() {
        return jwt().jwt(b -> b.subject(String.valueOf(SUPERVISOR)).claim("role", "SUPERVISOR")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private int approve(long groupId) throws Exception {
        return mockMvc.perform(post("/api/v1/request-groups/" + groupId + "/approve").with(supervisor()))
            .andReturn().getResponse().getStatus();
    }

    private String queueReference() throws Exception {
        String json = mockMvc.perform(get("/api/v1/review-queue").with(supervisor()))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.groupsAwaitingApproval[0].reference");
    }

    private PassengerRequestGroup onlyGroup() {
        List<PassengerRequestGroup> groups = groupRepository.findByShiftIdOrderByIdAsc(shift.getId());
        assertEquals(1, groups.size());
        return groups.getFirst();
    }

    private static <T> List<T> fireTogether(List<Callable<T>> calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.size());
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return call.call();
                }));
            }
            List<T> results = Collections.synchronizedList(new ArrayList<>());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    // US08 S2 / US20 S2: the threshold notifies the company, the approval then sends EmergencyActivated once
    @Test
    void thresholdSendsReviewRequiredOnlyAndApprovalThenSendsHighEmergencyOnce() throws Exception {
        assertEquals(201, submit(FIRST_USER));
        assertEquals(201, submit(FIRST_USER + 1));
        assertEquals(0, own.settledCount(Duration.ofMillis(300)));

        assertEquals(201, submit(FIRST_USER + 2));

        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        assertEquals(1, own.settledCount(Duration.ofMillis(400)));
        CapturingSink.Captured review = own.events().getFirst();
        assertEquals("PassengerReviewRequired", review.name());
        assertEquals(0, own.named("EmergencyActivated").size());
        JsonNode reviewPayload = objectMapper.valueToTree(review.payload());
        assertEquals("REVIEW", reviewPayload.get("priority").asString());
        assertEquals(3, reviewPayload.get("contributorCount").asInt());
        assertEquals(3, reviewPayload.get("requestCount").asInt());
        assertEquals(review.id(), reviewPayload.get("eventId").asString());

        assertEquals(201, submit(FIRST_USER + 3));
        assertEquals(1, own.settledCount(Duration.ofMillis(300)));

        PassengerRequestGroup group = onlyGroup();
        assertEquals(201, approve(group.getId()));
        assertTrue(own.awaitCount(2, Duration.ofSeconds(3)));
        CapturingSink.Captured emergency = own.events().get(1);
        assertEquals("EmergencyActivated", emergency.name());
        JsonNode payload = objectMapper.valueToTree(emergency.payload());
        assertEquals("HIGH", payload.get("priority").asString());
        assertEquals("PASSENGER_GROUP", payload.get("source").asString());
        assertEquals(review.id(), payload.get("groupReference").asString());
        assertEquals(emergency.id(), payload.get("eventId").asString());
        assertEquals(route.getName(), payload.get("shift").get("routeName").asString());
        assertTrue(payload.get("location").isNull());

        assertEquals(200, approve(group.getId()));
        assertEquals(200, approve(group.getId()));
        assertEquals(2, own.settledCount(Duration.ofMillis(400)));
        assertEquals(0, foreign.events().size());
    }

    // Point B: one function produces the event id and the review queue reference
    @Test
    void reviewRequiredEventIdEqualsTheReviewQueueReferenceForANewGroup() throws Exception {
        submit(FIRST_USER);
        submit(FIRST_USER + 1);
        submit(FIRST_USER + 2);
        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));

        PassengerRequestGroup group = onlyGroup();
        assertThat(group.getPublicReference()).isNotNull();
        assertEquals(group.getPublicReference(), own.events().getFirst().id());
        assertEquals(own.events().getFirst().id(), queueReference());
    }

    @Test
    void reviewRequiredEventIdEqualsTheReviewQueueReferenceForALegacyGroupWithoutStoredReference()
            throws Exception {
        PassengerRequestGroup legacy = groupRepository.saveAndFlush(PassengerRequestGroup.open(company.getId(),
            bus.getId(), shift.getId(), clock.instant()));
        jdbcTemplate.update("update passenger_request_group set public_reference = null where id = ?",
            legacy.getId());

        submit(FIRST_USER);
        submit(FIRST_USER + 1);
        submit(FIRST_USER + 2);
        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));

        PassengerRequestGroup stored = onlyGroup();
        assertThat(stored.getPublicReference()).isNull();
        String expected = PublicReference.groupRecordId(stored.getId(), null);
        assertEquals(expected, own.events().getFirst().id());
        assertEquals(expected, queueReference());
        assertEquals(expected, objectMapper.valueToTree(own.events().getFirst().payload())
            .get("eventId").asString());
    }

    // Point C: the threshold is crossed concurrently; exactly one PassengerReviewRequired
    @Test
    void eightSimultaneousFirstRequestsCrossTheThresholdOnceOverManyRounds() throws Exception {
        for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
            own.events().clear();
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                long userId = FIRST_USER + 100L * round + i;
                calls.add(() -> submit(userId));
            }

            List<Integer> statuses = fireTogether(calls);

            assertThat(statuses).as("round %d statuses", round).containsOnly(201);
            assertTrue(own.awaitCount(1, Duration.ofSeconds(3)), "round " + round);
            assertEquals(1, own.settledCount(Duration.ofMillis(300)), "round " + round);
            assertEquals("PassengerReviewRequired", own.events().getFirst().name());
            PassengerRequestGroup group = onlyGroup();
            assertEquals(GroupStatus.AWAITING_APPROVAL, group.getStatus(), "round " + round);
            assertEquals(group.getPublicReference(), own.events().getFirst().id());
            List<PassengerRequest> requests = requestRepository.findAll().stream()
                .filter(r -> r.getShiftId().equals(shift.getId())).toList();
            assertEquals(3, requests.stream().filter(r -> r.getStatus() == RequestStatus.COUNTED).count(),
                "round " + round);
            assertEquals(5, requests.stream().filter(r -> r.getStatus() == RequestStatus.ATTACHED).count(),
                "round " + round);
            clearShiftCases();
        }
    }

    @Test
    void eightSimultaneousApprovalsDeliverExactlyOneEmergencyActivated() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit(FIRST_USER + i);
        }
        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        PassengerRequestGroup group = onlyGroup();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> approve(group.getId()));
        }

        List<Integer> statuses = fireTogether(calls);

        assertEquals(1, statuses.stream().filter(s -> s == 201).count());
        assertEquals(7, statuses.stream().filter(s -> s == 200).count());
        assertTrue(own.awaitCount(2, Duration.ofSeconds(3)));
        assertEquals(2, own.settledCount(Duration.ofMillis(400)));
        assertEquals(1, own.named("EmergencyActivated").size());
    }

    @Test
    void reviewRequiredPayloadCarriesNoIdentityPhotoMessageOrInternalIds() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit(FIRST_USER + i);
        }
        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));

        JsonNode payload = objectMapper.valueToTree(own.events().getFirst().payload());
        String json = payload.toString();

        assertEquals(Set.of("eventId", "type", "priority", "reference", "occurredAt", "bus", "shift",
            "contributorCount", "requestCount"), fieldNames(payload));
        assertEquals(Set.of("busId", "plate"), fieldNames(payload.get("bus")));
        assertEquals(Set.of("shiftId", "routeId", "routeName"), fieldNames(payload.get("shift")));
        for (String forbidden : List.of(MESSAGE, "dni", "photo", "message", "passengerUserId", "passengerId", "userId", "journey",
                "requestId", "groupId", "companyId", "image")) {
            assertFalse(json.toLowerCase().contains(forbidden.toLowerCase()), forbidden + " in " + json);
        }
        for (int i = 0; i < 3; i++) {
            assertFalse(json.contains(String.valueOf(FIRST_USER + i)));
        }
    }

    @Test
    void approvedEmergencyPayloadCarriesNoIdentityPhotoMessageOrInternalIds() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit(FIRST_USER + i);
        }
        assertTrue(own.awaitCount(1, Duration.ofSeconds(3)));
        assertEquals(201, approve(onlyGroup().getId()));
        assertTrue(own.awaitCount(2, Duration.ofSeconds(3)));

        JsonNode payload = objectMapper.valueToTree(own.named("EmergencyActivated").getFirst().payload());
        String json = payload.toString();

        assertEquals(Set.of("eventId", "type", "priority", "source", "reference", "groupReference", "occurredAt",
            "bus", "shift", "location"), fieldNames(payload));
        for (String forbidden : List.of(MESSAGE, "dni", "photo", "message", "passengerUserId", "passengerId", "userId", "journey",
                "requestId", "groupId", "companyId", "supervisor", "image", "driver")) {
            assertFalse(json.toLowerCase().contains(forbidden.toLowerCase()), forbidden + " in " + json);
        }
    }
}
