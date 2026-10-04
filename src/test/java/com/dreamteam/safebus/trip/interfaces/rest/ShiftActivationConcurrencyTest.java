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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
class ShiftActivationConcurrencyTest {

    private static final String URL = "/api/v1/shifts/activate";
    private static final Instant T1 = Instant.parse("2030-06-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-06-01T16:00:00Z");

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
    Route route;
    ShiftAssignment assignment;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Concurrency Trip Company"));
        bus = busRepository.save(Bus.create(company.getId(), "CONC-P01", () -> "conc-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 600L, "Concurrency Trip Driver",
            () -> "CONC-DRV-QR-001", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "Concurrency Route", "X", "Y"));
        assignment = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
    }

    @AfterEach
    void tearDown() {
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    @Test
    void concurrentActivations_exactlyOneSucceeds() throws Exception {
        String requestBody = String.format(
            "{\"assignmentId\":%d,\"qrCredential\":\"CONC-DRV-QR-001\"}", assignment.getId());

        var driverJwt = jwt()
            .jwt(b -> b.subject("600").claim("role", "DRIVER"))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(2);

        Runnable task = () -> {
            try {
                barrier.await();
                MvcResult result = mockMvc.perform(post(URL).with(driverJwt)
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
        assertThat(driverShiftRepository.count()).isEqualTo(1);
    }
}
