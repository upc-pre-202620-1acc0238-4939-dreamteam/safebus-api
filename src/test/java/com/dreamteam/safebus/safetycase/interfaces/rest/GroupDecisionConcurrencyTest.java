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
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Not @Transactional and on the real clock: the writers must commit independently to contend for real.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupDecisionConcurrencyTest {

    private static final int ROUNDS = 10;
    private static final long FIRST_SUPERVISOR = 76001L;

    @Autowired MockMvc mockMvc;
    @Autowired Clock clock;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository shiftRepository;
    @Autowired PassengerRequestGroupRepository groupRepository;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Company company;
    private Bus bus;
    private Driver driver;
    private Route route;
    private ShiftAssignment assignment;
    private DriverShift shift;

    @BeforeEach
    void createFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("GDC Company " + suffix));
        bus = busRepository.save(Bus.create(company.getId(), "GDC-" + suffix, () -> "gdc-qr-" + suffix));
        driver = driverRepository.save(Driver.create(company.getId(),
            9_800_000L + Math.abs(suffix.hashCode() % 100_000), "GDC Driver", () -> "GDC-DRV-" + suffix,
            Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "GDC Route " + suffix, "A", "B"));
        assignment = assignmentRepository.save(ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(),
            clock.instant().minusSeconds(3600), clock.instant().plusSeconds(3600), 1L, clock));
        shift = shiftRepository.save(DriverShift.start(assignment.getId(), driver.getId(), bus.getId(),
            route.getId(), clock));
    }

    @AfterEach
    void deleteFixture() {
        clearShiftGroups();
        shiftRepository.delete(shift);
        assignmentRepository.delete(assignment);
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private void clearShiftGroups() {
        jdbcTemplate.update("delete from emergency where shift_id = ?", shift.getId());
        groupRepository.deleteAll(groupRepository.findByShiftIdOrderByIdAsc(shift.getId()));
    }

    private PassengerRequestGroup awaitingGroup() {
        PassengerRequestGroup group = PassengerRequestGroup.open(company.getId(), bus.getId(), shift.getId(),
            clock.instant());
        group.moveToAwaitingApproval(clock.instant());
        return groupRepository.saveAndFlush(group);
    }

    private MvcResult approve(long groupId, long supervisorId) throws Exception {
        return mockMvc.perform(post("/api/v1/request-groups/" + groupId + "/approve").with(supervisor(supervisorId)))
            .andReturn();
    }

    private MvcResult decline(long groupId, long supervisorId) throws Exception {
        return mockMvc.perform(post("/api/v1/request-groups/" + groupId + "/decline").with(supervisor(supervisorId))
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not credible\"}")).andReturn();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor supervisor(long id) {
        return jwt().jwt(b -> b.subject(String.valueOf(id)).claim("role", "SUPERVISOR")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private static List<MvcResult> fireTogether(List<Callable<MvcResult>> calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.size());
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return call.call();
                }));
            }
            List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long count(List<MvcResult> results, int status) {
        return results.stream().filter(r -> r.getResponse().getStatus() == status).count();
    }

    private List<Emergency> emergenciesOf(Long groupId) {
        return emergencyRepository.findAll().stream().filter(e -> groupId.equals(e.getGroupId())).toList();
    }

    @Test
    void eightSimultaneousApprovalsOfOneGroupCreateExactlyOneEmergencyOverManyRounds() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            PassengerRequestGroup group = awaitingGroup();
            List<Callable<MvcResult>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                long supervisorId = FIRST_SUPERVISOR + i;
                calls.add(() -> approve(group.getId(), supervisorId));
            }

            List<MvcResult> results = fireTogether(calls);

            assertThat(results).hasSize(8);
            assertThat(count(results, 201)).as("round %d created", round).isEqualTo(1);
            assertThat(count(results, 200)).as("round %d replays", round).isEqualTo(7);
            List<Emergency> emergencies = emergenciesOf(group.getId());
            assertThat(emergencies).as("round %d emergencies", round).hasSize(1);
            for (MvcResult result : results) {
                assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.emergencyId"))
                    .isEqualTo(emergencies.getFirst().getId());
            }
            PassengerRequestGroup stored = groupRepository.findById(group.getId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(GroupStatus.APPROVED);
            clearShiftGroups();
        }
    }

    @Test
    void anApproveAndADeclineAtTheSameTimeHaveExactlyOneWinner() throws Exception {
        int approvals = 0;
        int declines = 0;
        for (int round = 0; round < ROUNDS * 2; round++) {
            PassengerRequestGroup group = awaitingGroup();
            List<Callable<MvcResult>> calls = List.of(
                () -> approve(group.getId(), FIRST_SUPERVISOR),
                () -> decline(group.getId(), FIRST_SUPERVISOR + 1));

            List<MvcResult> results = fireTogether(calls);

            PassengerRequestGroup stored = groupRepository.findById(group.getId()).orElseThrow();
            int approveStatus = results.get(0).getResponse().getStatus();
            int declineStatus = results.get(1).getResponse().getStatus();
            if (stored.getStatus() == GroupStatus.APPROVED) {
                approvals++;
                assertThat(approveStatus).as("round %d", round).isEqualTo(201);
                assertThat(declineStatus).as("round %d", round).isEqualTo(409);
                assertThat(emergenciesOf(group.getId())).hasSize(1);
            } else {
                declines++;
                assertThat(stored.getStatus()).isEqualTo(GroupStatus.NOT_APPROVED);
                assertThat(declineStatus).as("round %d", round).isEqualTo(200);
                assertThat(approveStatus).as("round %d", round).isEqualTo(409);
                assertThat(emergenciesOf(group.getId())).isEmpty();
                assertThat(stored.getOpenMarker()).isNull();
            }
            clearShiftGroups();
        }
        assertThat(approvals + declines).isEqualTo(ROUNDS * 2);
    }

    @Test
    void fourApprovalsRacingFourDeclinesStillHaveOneWinner() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            PassengerRequestGroup group = awaitingGroup();
            List<Callable<MvcResult>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                long approver = FIRST_SUPERVISOR + i;
                long decliner = FIRST_SUPERVISOR + 10 + i;
                calls.add(() -> approve(group.getId(), approver));
                calls.add(() -> decline(group.getId(), decliner));
            }

            List<MvcResult> results = fireTogether(calls);

            PassengerRequestGroup stored = groupRepository.findById(group.getId()).orElseThrow();
            List<MvcResult> approves = new ArrayList<>();
            List<MvcResult> declines = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                (i % 2 == 0 ? approves : declines).add(results.get(i));
            }
            if (stored.getStatus() == GroupStatus.APPROVED) {
                assertThat(count(approves, 201)).as("round %d", round).isEqualTo(1);
                assertThat(count(approves, 200)).isEqualTo(3);
                assertThat(count(declines, 409)).isEqualTo(4);
                assertThat(emergenciesOf(group.getId())).hasSize(1);
            } else {
                assertThat(stored.getStatus()).isEqualTo(GroupStatus.NOT_APPROVED);
                assertThat(count(declines, 200)).as("round %d", round).isEqualTo(4);
                assertThat(count(approves, 409)).isEqualTo(4);
                assertThat(emergenciesOf(group.getId())).isEmpty();
            }
            clearShiftGroups();
        }
    }
}
