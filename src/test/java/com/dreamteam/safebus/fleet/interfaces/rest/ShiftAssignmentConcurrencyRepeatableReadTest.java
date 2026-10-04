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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ")
class ShiftAssignmentConcurrencyRepeatableReadTest {

    private static final String URL = "/api/v1/shift-assignments";
    private static final Instant T1 = Instant.parse("2025-08-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2025-08-01T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(T1, ZoneOffset.UTC);

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;

    Company company;
    Bus bus;
    Driver driver;
    Route route;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("RR Concurrency Company"));
        bus = busRepository.save(Bus.create(company.getId(), "RR-BUS1", () -> "rr-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 600L, "RR Concurrency Driver",
            () -> "rr-cred1", Duration.ofDays(365), FIXED_CLOCK));
        route = routeRepository.save(Route.create(company.getId(), "RR Concurrency Route", "P", "Q"));
    }

    @AfterEach
    void tearDown() {
        assignmentRepository.deleteAll();
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    @Test
    void concurrentOverlappingRequests_repeatableRead_exactlyOneSucceeds() throws Exception {
        String requestBody = String.format(
            "{\"driverId\":%d,\"busId\":%d,\"routeId\":%d,\"plannedStart\":\"%s\",\"plannedEnd\":\"%s\"}",
            driver.getId(), bus.getId(), route.getId(), T1, T2);

        var supervisorJwt = jwt()
            .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", company.getId()).subject("42"))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(2);

        Runnable task = () -> {
            try {
                barrier.await();
                MvcResult result = mockMvc.perform(post(URL).with(supervisorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                    .andReturn();
                statuses.add(result.getResponse().getStatus());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(assignmentRepository.count()).isEqualTo(1);
    }
}
