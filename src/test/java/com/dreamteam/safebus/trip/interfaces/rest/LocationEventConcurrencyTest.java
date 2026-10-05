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
class LocationEventConcurrencyTest {

    private static final String URL = "/api/v1/location-events";
    private static final Instant T1 = Instant.parse("2030-10-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-10-01T16:00:00Z");

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
    Driver driver1;
    Driver driver2;
    Route route;
    DriverShift shift1;
    DriverShift shift2;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("CONC LOC Company"));
        bus = busRepository.save(Bus.create(company.getId(), "CON-BUS01", () -> "con-bus-qr1"));
        driver1 = driverRepository.save(Driver.create(company.getId(), 1400L, "CON Driver 1",
            () -> "CON-DRV-QR-001", Duration.ofDays(365), clock));
        driver2 = driverRepository.save(Driver.create(company.getId(), 1401L, "CON Driver 2",
            () -> "CON-DRV-QR-002", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "CON Route", "A", "B"));

        ShiftAssignment assignment1 = assignmentRepository.save(
            ShiftAssignment.create(driver1.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        ShiftAssignment assignment2 = assignmentRepository.save(
            ShiftAssignment.create(driver2.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));

        shift1 = driverShiftRepository.save(
            DriverShift.start(assignment1.getId(), driver1.getId(), bus.getId(), route.getId(), clock));
        shift2 = driverShiftRepository.save(
            DriverShift.start(assignment2.getId(), driver2.getId(), bus.getId(), route.getId(), clock));
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

    private String body(String eventId, long shiftId, Instant capturedAt) {
        return String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,\"latitude\":-12.046,\"longitude\":-77.042}",
            eventId, shiftId, capturedAt.toString());
    }

    @Test
    void eightIdenticalConcurrentRetries_exactlyOne201SevenReturns200OneRow() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Instant capturedAt = Instant.now(clock).minusSeconds(10);
        String requestBody = body(eventId, shift1.getId(), capturedAt);
        var driverJwt = driverJwt(1400L);
        int threads = 8;

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            threadList.add(new Thread(() -> {
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
            }));
        }
        threadList.forEach(Thread::start);
        for (Thread t : threadList) t.join();

        assertThat(statuses).hasSize(threads);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(7);
        assertThat(locationEventRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentEventsForSameBusWithDifferentCapturedAt_finalPositionHasGreatestCapturedAt()
            throws Exception {
        // No VehicleLocation row yet — exercises the race on creating the row (unique constraint on busId)
        assertThat(vehicleLocationRepository.findByBusId(bus.getId())).isEmpty();

        Instant earlier = Instant.now(clock).minusSeconds(30);
        Instant later = Instant.now(clock).minusSeconds(5);

        String body1 = body(UUID.randomUUID().toString(), shift1.getId(), earlier);
        String body2 = body(UUID.randomUUID().toString(), shift2.getId(), later);

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                MvcResult r = mockMvc.perform(post(URL).with(driverJwt(1400L))
                        .contentType(MediaType.APPLICATION_JSON).content(body1))
                    .andReturn();
                statuses.add(r.getResponse().getStatus());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                MvcResult r = mockMvc.perform(post(URL).with(driverJwt(1401L))
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                    .andReturn();
                statuses.add(r.getResponse().getStatus());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        t1.start(); t2.start();
        t1.join(); t2.join();

        assertThat(statuses).containsOnly(201);
        assertThat(locationEventRepository.count()).isEqualTo(2);

        var vl = vehicleLocationRepository.findByBusId(bus.getId()).orElseThrow();
        assertThat(vl.getCapturedAt()).isEqualTo(
            later.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }
}
