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
import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Not @Transactional and on the real clock: the endpoints must commit independently to contend for real.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JourneyEndConcurrencyTest {

    private static final int ROUNDS = 10;
    private static final int END_THREADS = 8;
    private static final Duration JOURNEY_AGE = Duration.ofHours(1);
    private static final long PASSENGER_BASE = 63000L;

    private static final AtomicInteger RUNS = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired Clock clock;
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

    private record Fixture(Bus bus, Driver driver, ShiftAssignment assignment, DriverShift shift) {}

    private Company company;
    private Route route;
    private final List<Fixture> fixtures = new ArrayList<>();
    private final List<Long> journeyIds = new ArrayList<>();
    private int sequence;

    @BeforeEach
    void createCompany() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("JEC Company " + suffix));
        route = routeRepository.save(Route.create(company.getId(), "JEC Route " + suffix, "A", "B"));
    }

    @AfterEach
    void deleteFixtures() {
        for (Fixture f : fixtures) {
            List<PassengerRequest> requests = requestRepository.findAll().stream()
                .filter(r -> r.getShiftId().equals(f.shift().getId())).toList();
            requestRepository.deleteAll(requests);
            storedImageRepository.deleteAllById(requests.stream().map(PassengerRequest::getEvidenceImageId).toList());
            groupRepository.deleteAll(groupRepository.findByShiftIdOrderByIdAsc(f.shift().getId()));
        }
        journeyRepository.deleteAllById(journeyIds);
        for (Fixture f : fixtures) {
            shiftRepository.delete(f.shift());
            assignmentRepository.delete(f.assignment());
            driverRepository.delete(f.driver());
            busRepository.delete(f.bus());
        }
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private Fixture newFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Bus bus = busRepository.save(Bus.create(company.getId(), "JEC-" + suffix, () -> "jec-qr-" + suffix));
        Driver driver = driverRepository.save(Driver.create(company.getId(),
            9_700_000L + (++sequence) * 7L + Math.abs(suffix.hashCode() % 1000) * 1000L, "JEC Driver",
            () -> "JEC-DRV-" + suffix, Duration.ofDays(365), clock));
        ShiftAssignment assignment = ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(),
            clock.instant().minusSeconds(7200), clock.instant().plusSeconds(7200), 1L, clock);
        assignment.activate();
        assignment = assignmentRepository.save(assignment);
        DriverShift shift = shiftRepository.save(DriverShift.start(assignment.getId(), driver.getId(), bus.getId(),
            route.getId(), clock));
        Fixture fixture = new Fixture(bus, driver, assignment, shift);
        fixtures.add(fixture);
        return fixture;
    }

    // Started an hour ago on a fixed clock so any endedAt up to now is valid and a long summary fits.
    private PassengerJourney journeyOf(long userId, Fixture f) {
        Clock past = Clock.fixed(clock.instant().minus(JOURNEY_AGE), ZoneOffset.UTC);
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(userId, f.bus().getId(), f.shift().getId(), past));
        journeyIds.add(journey.getId());
        return journey;
    }

    private static RequestPostProcessor jwtOf(long userId, String role) {
        return jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", role))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private record Response(int status, String body) {}

    private Response end(long userId, long journeyId, String body) throws Exception {
        var response = mockMvc.perform(post("/api/v1/journeys/" + journeyId + "/end")
                .with(jwtOf(userId, "PASSENGER")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse();
        return new Response(response.getStatus(), response.getContentAsString());
    }

    // Thread i sends a different reason, endedAt and summary; only AUTOMATIC_SEPARATION may carry a summary.
    private String endPayload(PassengerJourney journey, int i, boolean withEndedAt) {
        String endedAt = withEndedAt
            ? ",\"endedAt\":\"" + journey.getStartedAt().plusSeconds(60L * (i + 1)) + "\"" : "";
        return switch (i % 3) {
            case 0 -> "{\"reason\":\"MANUAL\"" + endedAt + "}";
            case 1 -> "{\"reason\":\"SIGN_OUT\"" + endedAt + "}";
            default -> "{\"reason\":\"AUTOMATIC_SEPARATION\"" + endedAt
                + ",\"summary\":{\"separationDistanceMeters\":" + (120 + i)
                + ",\"separationDurationSeconds\":" + (61 + i) + "}}";
        };
    }

    private Response close(Fixture f) throws Exception {
        var response = mockMvc.perform(post("/api/v1/shifts/" + f.shift().getId() + "/close")
                .with(jwtOf(f.driver().getUserAccountId(), "DRIVER")))
            .andReturn().getResponse();
        return new Response(response.getStatus(), response.getContentAsString());
    }

    private Response submitRequest(long userId, long journeyId, String id, Instant activatedAt) throws Exception {
        var response = mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", TestImages.jpeg(11)))
                .param("id", id)
                .param("journeyId", String.valueOf(journeyId))
                .param("message", "Danger on board")
                .param("activatedAt", activatedAt.toString())
                .with(jwtOf(userId, "PASSENGER")))
            .andReturn().getResponse();
        return new Response(response.getStatus(), response.getContentAsString());
    }

    private List<Response> fireTogether(List<Callable<Response>> calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.size());
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (Callable<Response> call : calls) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    RUNS.incrementAndGet();
                    return call.call();
                }));
            }
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String field(Response response, String path) {
        return String.valueOf((Object) JsonPath.read(response.body(), path));
    }

    private void assertSingleWinnerAndReplays(PassengerJourney journey, List<Response> ends, int round) {
        PassengerJourney stored = journeyRepository.findById(journey.getId()).orElseThrow();
        assertThat(stored.getStatus()).as("round %d", round).isEqualTo(JourneyStatus.ENDED);
        assertThat(ends).as("round %d", round).allSatisfy(r -> assertThat(r.status()).isEqualTo(200));
        assertThat(ends.stream().map(Response::body).distinct())
            .as("round %d: every response carries the original data", round).hasSize(1);
        Response any = ends.getFirst();
        assertThat(field(any, "$.status")).isEqualTo("ENDED");
        assertThat(field(any, "$.endReason")).isEqualTo(stored.getEndReason().name());
        assertThat(Instant.parse(field(any, "$.endedAt"))).isEqualTo(stored.getEndedAt());
        if (stored.getSeparationSummary() == null) {
            assertThat(stored.getEndReason()).isNotEqualTo(JourneyEndReason.AUTOMATIC_SEPARATION);
            assertThat(any.body()).doesNotContain("\"summary\":{");
        } else {
            assertThat(stored.getEndReason()).isEqualTo(JourneyEndReason.AUTOMATIC_SEPARATION);
            assertThat(field(any, "$.summary.separationDistanceMeters"))
                .isEqualTo(String.valueOf(stored.getSeparationSummary().distanceMeters()));
            assertThat(field(any, "$.summary.separationDurationSeconds"))
                .isEqualTo(String.valueOf(stored.getSeparationSummary().durationSeconds()));
        }
    }

    @Test
    void eightEndsWithDifferentReasonsTimesAndSummariesLeaveOneOriginalRecordThatEveryResponseReturns()
            throws Exception {
        Fixture f = newFixture();
        int before = RUNS.get();
        for (int round = 0; round < ROUNDS; round++) {
            long user = PASSENGER_BASE + round;
            PassengerJourney journey = journeyOf(user, f);
            List<Callable<Response>> calls = new ArrayList<>();
            for (int i = 0; i < END_THREADS; i++) {
                String body = endPayload(journey, i, i % 2 == 0);
                calls.add(() -> end(user, journey.getId(), body));
            }

            assertSingleWinnerAndReplays(journey, fireTogether(calls), round);
        }
        assertThat(RUNS.get() - before).isEqualTo(ROUNDS * END_THREADS);
    }

    @Test
    void endsRacingASubmittedRequestNeverLoseTheRequestOrTheOriginalEndData() throws Exception {
        Fixture f = newFixture();
        int before = RUNS.get();
        for (int round = 0; round < ROUNDS; round++) {
            long user = PASSENGER_BASE + 100 + round;
            PassengerJourney journey = journeyOf(user, f);
            boolean activatedAfterReportedEnd = round % 2 == 1;
            String id = UUID.randomUUID().toString();
            Instant activatedAt = clock.instant().minusSeconds(2);
            // Odd rounds: every end reports an endedAt before the request's activatedAt, so the request is either
            // accepted before the end wins or rejected by the 6a rule after it; never half stored.
            List<Callable<Response>> calls = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String body = activatedAfterReportedEnd
                    ? "{\"reason\":\"MANUAL\",\"endedAt\":\"" + journey.getStartedAt().plusSeconds(10L + i) + "\"}"
                    : endPayload(journey, i, false);
                calls.add(() -> end(user, journey.getId(), body));
            }
            calls.add(() -> submitRequest(user, journey.getId(), id, activatedAt));

            List<Response> responses = fireTogether(calls);

            assertSingleWinnerAndReplays(journey, responses.subList(0, 3), round);
            Response request = responses.get(3);
            Optional<PassengerRequest> stored = requestRepository.findById(id);
            if (activatedAfterReportedEnd) {
                assertThat(request.status()).as("round %d", round).isIn(201, 422);
            } else {
                assertThat(request.status()).as("round %d", round).isEqualTo(201);
            }
            if (request.status() == 201) {
                assertThat(stored).as("round %d", round).isPresent();
                assertThat(stored.get().getStatus()).isIn(RequestStatus.COUNTED, RequestStatus.LATE);
                assertThat(stored.get().getJourneyId()).isEqualTo(journey.getId());
            } else {
                assertThat(field(request, "$.code")).isEqualTo("ACTIVATION_AFTER_JOURNEY_END");
                assertThat(stored).as("round %d: a rejected request leaves no row", round).isEmpty();
            }
        }
        assertThat(RUNS.get() - before).isEqualTo(ROUNDS * 4);
    }

    @Test
    void endsRacingTheShiftClosureEndTheJourneyOnceAndEveryResponseReturnsTheOriginalData() throws Exception {
        int before = RUNS.get();
        for (int round = 0; round < ROUNDS; round++) {
            Fixture f = newFixture();
            long user = PASSENGER_BASE + 200 + round;
            PassengerJourney journey = journeyOf(user, f);
            List<Callable<Response>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String body = endPayload(journey, i, i % 2 == 0);
                calls.add(() -> end(user, journey.getId(), body));
            }
            calls.add(() -> close(f));

            List<Response> responses = fireTogether(calls);

            assertThat(responses.get(4).status()).as("round %d: the closure", round).isEqualTo(200);
            assertSingleWinnerAndReplays(journey, responses.subList(0, 4), round);
            DriverShift closed = shiftRepository.findById(f.shift().getId()).orElseThrow();
            assertThat(closed.getStatus()).isEqualTo(ShiftStatus.CLOSED);
            PassengerJourney stored = journeyRepository.findById(journey.getId()).orElseThrow();
            if (stored.getEndReason() == JourneyEndReason.SHIFT_CLOSED) {
                assertThat(stored.getEndedAt()).isEqualTo(closed.getClosedAt());
                assertThat(stored.getSeparationSummary()).isNull();
            }
        }
        assertThat(RUNS.get() - before).isEqualTo(ROUNDS * 5);
    }
}
