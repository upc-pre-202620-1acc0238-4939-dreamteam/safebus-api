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
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

// Not @Transactional and on the real clock: the writers must commit independently to contend for real.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PassengerRequestConcurrencyTest {

    private static final String URL = "/api/v1/passenger-requests";
    private static final long FIRST_USER = 65001L;

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

    private Company company;
    private Bus bus;
    private Driver driver;
    private Route route;
    private ShiftAssignment assignment;
    private DriverShift shift;
    private final List<Long> journeyIds = new ArrayList<>();

    @BeforeEach
    void createFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("PRC Company " + suffix));
        bus = busRepository.save(Bus.create(company.getId(), "PRC-" + suffix, () -> "prc-qr-" + suffix));
        driver = driverRepository.save(Driver.create(company.getId(),
            9_500_000L + Math.abs(suffix.hashCode() % 400_000), "PRC Driver", () -> "PRC-DRV-" + suffix,
            Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "PRC Route " + suffix, "A", "B"));
        assignment = assignmentRepository.save(ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(),
            clock.instant().minusSeconds(3600), clock.instant().plusSeconds(3600), 1L, clock));
        shift = shiftRepository.save(DriverShift.start(assignment.getId(), driver.getId(), bus.getId(),
            route.getId(), clock));
    }

    @AfterEach
    void deleteFixture() {
        List<PassengerRequest> requests = requestRepository.findAll().stream()
            .filter(r -> r.getShiftId().equals(shift.getId())).toList();
        requestRepository.deleteAll(requests);
        storedImageRepository.deleteAllById(requests.stream().map(PassengerRequest::getEvidenceImageId).toList());
        groupRepository.deleteAll(groupRepository.findByShiftIdOrderByIdAsc(shift.getId()));
        journeyRepository.deleteAllById(journeyIds);
        shiftRepository.delete(shift);
        assignmentRepository.delete(assignment);
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private PassengerJourney journeyOf(long userId) {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(userId, bus.getId(), shift.getId(), clock));
        journeyIds.add(journey.getId());
        return journey;
    }

    private int post(long userId, long journeyId, String id, Instant activatedAt) throws Exception {
        return mockMvc.perform(multipart(URL)
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", TestImages.jpeg(11)))
                .param("id", id)
                .param("journeyId", String.valueOf(journeyId))
                .param("message", "Danger on board")
                .param("activatedAt", activatedAt.toString())
                .with(jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andReturn().getResponse().getStatus();
    }

    private List<Integer> fireTogether(List<Callable<Integer>> calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.size());
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return call.call();
                }));
            }
            List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    @Test
    void eightIdenticalRetriesGiveOne201SevenOk200AndOneRow() throws Exception {
        PassengerJourney journey = journeyOf(FIRST_USER);
        String id = UUID.randomUUID().toString();
        Instant activatedAt = clock.instant().minusSeconds(2);
        long images = storedImageRepository.count();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> post(FIRST_USER, journey.getId(), id, activatedAt));
        }

        List<Integer> statuses = fireTogether(calls);

        assertThat(statuses).hasSize(8);
        assertThat(count(statuses, 201)).isEqualTo(1);
        assertThat(count(statuses, 200)).isEqualTo(7);
        assertThat(requestRepository.findAll().stream().filter(r -> r.getShiftId().equals(shift.getId())))
            .hasSize(1);
        assertThat(groupRepository.findByShiftIdOrderByIdAsc(shift.getId())).hasSize(1);
        assertThat(storedImageRepository.count() - images).isEqualTo(1);
    }

    @Test
    void eightIdenticalRetriesAgainstAnExistingGroupAreServedFromTheCommittedOriginalAfterTheLock()
            throws Exception {
        groupRepository.saveAndFlush(PassengerRequestGroup.open(company.getId(), bus.getId(), shift.getId(),
            clock.instant()));
        PassengerJourney journey = journeyOf(FIRST_USER);
        String id = UUID.randomUUID().toString();
        Instant activatedAt = clock.instant().minusSeconds(2);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> post(FIRST_USER, journey.getId(), id, activatedAt));
        }

        List<Integer> statuses = fireTogether(calls);

        assertThat(count(statuses, 201)).isEqualTo(1);
        assertThat(count(statuses, 200)).isEqualTo(7);
        assertThat(requestRepository.findAll().stream().filter(r -> r.getShiftId().equals(shift.getId())))
            .hasSize(1);
    }

    @Test
    void fiveDifferentPassengersOnOneShiftWithNoGroupYetCreateOneGroupThreeCountedAndTwoAttached()
            throws Exception {
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long user = FIRST_USER + i;
            PassengerJourney journey = journeyOf(user);
            calls.add(() -> post(user, journey.getId(), UUID.randomUUID().toString(), clock.instant().minusSeconds(2)));
        }

        List<Integer> statuses = fireTogether(calls);

        assertThat(statuses).hasSize(5).allMatch(s -> s == 201);
        List<PassengerRequestGroup> groups = groupRepository.findByShiftIdOrderByIdAsc(shift.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getStatus()).isEqualTo(GroupStatus.AWAITING_APPROVAL);
        List<PassengerRequest> requests = requestRepository.findAll().stream()
            .filter(r -> r.getShiftId().equals(shift.getId())).toList();
        assertThat(requests).hasSize(5);
        assertThat(requests.stream().filter(r -> r.getStatus() == RequestStatus.COUNTED)).hasSize(3);
        assertThat(requests.stream().filter(r -> r.getStatus() == RequestStatus.ATTACHED)).hasSize(2);
        assertThat(requests).allMatch(r -> groups.getFirst().getId().equals(r.getGroupId()));
    }

    @Test
    void twoDifferentIdsFromTheSamePassengerGiveOne201AndOne409() throws Exception {
        PassengerJourney journey = journeyOf(FIRST_USER);
        long images = storedImageRepository.count();
        List<Callable<Integer>> calls = List.of(
            () -> post(FIRST_USER, journey.getId(), UUID.randomUUID().toString(), clock.instant().minusSeconds(2)),
            () -> post(FIRST_USER, journey.getId(), UUID.randomUUID().toString(), clock.instant().minusSeconds(2)));

        List<Integer> statuses = fireTogether(calls);

        assertThat(count(statuses, 201)).isEqualTo(1);
        assertThat(count(statuses, 409)).isEqualTo(1);
        assertThat(requestRepository.findAll().stream().filter(r -> r.getShiftId().equals(shift.getId())))
            .hasSize(1);
        assertThat(storedImageRepository.count() - images).isEqualTo(1);
    }
}
