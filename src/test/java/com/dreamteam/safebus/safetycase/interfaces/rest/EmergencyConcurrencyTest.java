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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmergencyConcurrencyTest {

    private static final long DRIVER_USER_ID  = 7000L;
    private static final long COMPANY_ID      = 700L;
    private static final long SUPERVISOR_ID   = 7999L;
    private static final Instant T1 = Instant.parse("2030-11-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-11-01T16:00:00Z");

    @Autowired MockMvc               mockMvc;
    @Autowired CompanyRepository     companyRepository;
    @Autowired BusRepository         busRepository;
    @Autowired DriverRepository      driverRepository;
    @Autowired RouteRepository       routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired EmergencyRepository   emergencyRepository;
    @Autowired Clock clock;

    Company     company;
    Bus         bus;
    Driver      driver;
    Route       route;
    DriverShift shift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("CONC SC Company"));
        bus     = busRepository.save(Bus.create(company.getId(), "SC-BUS01", () -> "sc-bus-qr1"));
        driver  = driverRepository.save(Driver.create(company.getId(), DRIVER_USER_ID,
                      "SC Driver", () -> "SC-DRV-QR-001", Duration.ofDays(365), clock));
        route   = routeRepository.save(Route.create(company.getId(), "SC Route", "A", "B"));
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

    private org.springframework.test.web.servlet.request.RequestPostProcessor driverJwt() {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(DRIVER_USER_ID)).claim("role", "DRIVER"))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor supervisorJwt() {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(SUPERVISOR_ID))
                .claim("role", "SUPERVISOR")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    // (1) 8 simultaneous identical creations → exactly one 201, seven 200, one row
    @Test
    void eightIdenticalConcurrentCreations_exactlyOne201SevenReturns200OneRow() throws Exception {
        String id = UUID.randomUUID().toString();
        Instant activatedAt = Instant.now(clock).minusSeconds(10);
        String reqBody = "{\"id\":\"" + id + "\",\"shiftId\":" + shift.getId()
            + ",\"activatedAt\":\"" + activatedAt + "\"}";

        int threads = 8;
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            threadList.add(new Thread(() -> {
                try {
                    barrier.await();
                    int status = mockMvc.perform(post("/api/v1/driver-emergencies")
                            .with(driverJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reqBody))
                        .andReturn().getResponse().getStatus();
                    statuses.add(status);
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        threadList.forEach(Thread::start);
        for (Thread t : threadList) t.join();

        assertThat(statuses).hasSize(threads);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(7);
        assertThat(emergencyRepository.count()).isEqualTo(1);
    }

    // (2) two supervisors starting attention simultaneously → exactly one 200, one 409
    @Test
    void twoSupervisorsStartAttentionConcurrently_oneSucceedsOneFails() throws Exception {
        Emergency emergency = emergencyRepository.save(
            Emergency.activateByDriver(
                UUID.randomUUID().toString(),
                company.getId(), driver.getId(), bus.getId(), shift.getId(), route.getId(), null,
                Instant.now(clock).minusSeconds(60), clock));

        String url = "/api/v1/emergencies/" + emergency.getId() + "/start-attention";

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                statuses.add(mockMvc.perform(post(url).with(supervisorJwt()))
                    .andReturn().getResponse().getStatus());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                statuses.add(mockMvc.perform(post(url).with(supervisorJwt()))
                    .andReturn().getResponse().getStatus());
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        t1.start(); t2.start();
        t1.join();  t2.join();

        assertThat(statuses).hasSize(2);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(1);
    }
}
