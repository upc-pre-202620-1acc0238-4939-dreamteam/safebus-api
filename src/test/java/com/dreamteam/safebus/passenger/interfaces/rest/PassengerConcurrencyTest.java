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
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.passenger.domain.repository.PassengerAccountRepository;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PassengerConcurrencyTest {

    private static final String PASSENGERS_URL = "/api/v1/passengers";
    private static final String JOURNEYS_URL   = "/api/v1/journeys";
    private static final Instant T1 = Instant.parse("2030-06-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-06-01T20:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired PassengerAccountRepository passengerAccountRepository;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired StoredImageRepository storedImageRepository;
    @Autowired Clock clock;

    Company company;
    Bus bus1, bus2;
    Driver driver;
    Route route;
    ShiftAssignment sa1, sa2;
    DriverShift shift1, shift2;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("PCONC Co"));
        bus1    = busRepository.save(Bus.create(company.getId(), "PCONC-BUS1", () -> "PCONC-QR-001"));
        bus2    = busRepository.save(Bus.create(company.getId(), "PCONC-BUS2", () -> "PCONC-QR-002"));
        driver  = driverRepository.save(Driver.create(company.getId(), 66601L, "PCONC Drv",
                      () -> "PCONC-DRV-QR", Duration.ofDays(365), clock));
        route   = routeRepository.save(Route.create(company.getId(), "PCONC Route", "P", "Q"));
        sa1     = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus1.getId(), route.getId(), T1, T2, 1L, clock));
        sa2     = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus2.getId(), route.getId(), T1, T2, 1L, clock));
        shift1  = driverShiftRepository.save(
            DriverShift.start(sa1.getId(), driver.getId(), bus1.getId(), route.getId(), clock));
        shift2  = driverShiftRepository.save(
            DriverShift.start(sa2.getId(), driver.getId(), bus2.getId(), route.getId(), clock));
    }

    @AfterEach
    void tearDown() {
        journeyRepository.deleteAll();
        passengerAccountRepository.deleteAll();
        userAccountRepository.findByLoginId("31415926").ifPresent(userAccountRepository::delete);
        storedImageRepository.deleteAll();
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.deleteAll();
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    // --- Concurrency test 1: 8 threads same DNI ---

    @Test
    void eightConcurrentRegistrationsSameDni_exactlyOne201Seven409OneRow() throws Exception {
        int threads = 8;
        String sharedDni = "31415926";
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(threads);
        byte[] photo = validJpeg();

        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int idx = i;
            threadList.add(new Thread(() -> {
                try {
                    barrier.await();
                    int status = mockMvc.perform(multipart(PASSENGERS_URL)
                            .file(new MockMultipartFile("facePhoto", "p.jpg", "image/jpeg", photo))
                            .param("password", "Password1!")
                            .param("dni", sharedDni)
                            .param("termsAccepted", "true")
                            .param("termsVersion", "2026-10"))
                        .andReturn().getResponse().getStatus();
                    statuses.add(status);
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        threadList.forEach(Thread::start);
        for (Thread t : threadList) t.join();

        assertThat(statuses).hasSize(threads);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(threads - 1);
        assertThat(passengerAccountRepository.count()).isEqualTo(1);
    }

    // --- Concurrency test 2: 8 identical journey starts ---

    @Test
    void eightIdenticalJourneyStarts_exactlyOne201Seven200OneRow() throws Exception {
        int threads = 8;
        long passengerId = 66700L;
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            threadList.add(new Thread(() -> {
                try {
                    barrier.await();
                    int status = mockMvc.perform(post(JOURNEYS_URL)
                            .with(passengerJwt(passengerId))
                            .contentType(APPLICATION_JSON)
                            .content("{\"busQrCode\":\"PCONC-QR-001\"}"))
                        .andReturn().getResponse().getStatus();
                    statuses.add(status);
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        threadList.forEach(Thread::start);
        for (Thread t : threadList) t.join();

        assertThat(statuses).hasSize(threads);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(threads - 1);
        assertThat(journeyRepository.count()).isEqualTo(1);
    }

    // --- Concurrency test 3: 2 simultaneous starts on different buses ---

    @Test
    void twoConcurrentStartsOnDifferentBuses_oneSucceedsOneConflicts() throws Exception {
        long passengerId = 66800L;
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                statuses.add(mockMvc.perform(post(JOURNEYS_URL)
                        .with(passengerJwt(passengerId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"busQrCode\":\"PCONC-QR-001\"}"))
                    .andReturn().getResponse().getStatus());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                statuses.add(mockMvc.perform(post(JOURNEYS_URL)
                        .with(passengerJwt(passengerId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"busQrCode\":\"PCONC-QR-002\"}"))
                    .andReturn().getResponse().getStatus());
            } catch (Exception e) { throw new RuntimeException(e); }
        });

        t1.start(); t2.start();
        t1.join();  t2.join();

        assertThat(statuses).hasSize(2);
        long successCount = statuses.stream().filter(s -> s == 201 || s == 200).count();
        long conflictCount = statuses.stream().filter(s -> s == 409).count();
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);
        assertThat(journeyRepository.count()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor passengerJwt(long userId) {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    private static byte[] validJpeg() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
}
