package com.dreamteam.safebus.notification.support;

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
import com.dreamteam.safebus.notification.application.SubscriptionRegistry;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A company with two buses, each with its own driver and shift, behind a real server on the real clock.
 * The tests here exercise the whole path over HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class LiveStreamFixture {

    protected static final Duration TOKEN_VALIDITY = Duration.ofMinutes(10);
    private static final long FIRST_DRIVER_USER = 71001L;

    @LocalServerPort protected int port;
    @Autowired protected JwtEncoder jwtEncoder;
    @Autowired protected Clock clock;
    @Autowired protected SubscriptionRegistry registry;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected PassengerRequestGroupRepository groupRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository shiftRepository;

    protected Company company;
    protected final List<Bus> buses = new ArrayList<>();
    protected final List<Driver> drivers = new ArrayList<>();
    protected final List<DriverShift> shifts = new ArrayList<>();
    private final List<ShiftAssignment> assignments = new ArrayList<>();
    private Route route;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<SseTestClient> streams = new ArrayList<>();

    @BeforeEach
    void createLiveFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("LIVE Company " + suffix));
        route = routeRepository.save(Route.create(company.getId(), "LIVE Route " + suffix, "A", "B"));
        for (int i = 0; i < 2; i++) {
            Bus bus = busRepository.save(Bus.create(company.getId(), "LV" + i + "-" + suffix,
                () -> "live-qr-" + UUID.randomUUID()));
            Driver driver = driverRepository.save(Driver.create(company.getId(), FIRST_DRIVER_USER + i,
                "Live Driver " + i, () -> "LIVE-DRV-" + UUID.randomUUID(), Duration.ofDays(365), clock));
            ShiftAssignment assignment = assignmentRepository.save(ShiftAssignment.create(driver.getId(),
                bus.getId(), route.getId(), clock.instant().minusSeconds(3600),
                clock.instant().plusSeconds(3600), 1L, clock));
            shifts.add(shiftRepository.save(DriverShift.start(assignment.getId(), driver.getId(), bus.getId(),
                route.getId(), clock)));
            buses.add(bus);
            drivers.add(driver);
            assignments.add(assignment);
        }
    }

    @AfterEach
    void deleteLiveFixture() {
        streams.forEach(SseTestClient::close);
        registry.all().forEach(registry::remove);
        for (DriverShift shift : shifts) {
            jdbcTemplate.update("delete from emergency where shift_id = ?", shift.getId());
            groupRepository.deleteAll(groupRepository.findByShiftIdOrderByIdAsc(shift.getId()));
        }
        shiftRepository.deleteAll(shifts);
        assignmentRepository.deleteAll(assignments);
        driverRepository.deleteAll(drivers);
        busRepository.deleteAll(buses);
        routeRepository.delete(route);
        companyRepository.delete(company);
        shifts.clear();
        assignments.clear();
        drivers.clear();
        buses.clear();
    }

    protected String supervisorToken(long userId, long companyId) {
        return new StreamTokens(jwtEncoder).supervisor(userId, companyId, TOKEN_VALIDITY);
    }

    protected SseTestClient stream(int viaPort, String bearer) throws Exception {
        SseTestClient client = SseTestClient.connect("localhost", viaPort, "/api/v1/notifications/stream", bearer);
        streams.add(client);
        return client;
    }

    protected void closeStream(SseTestClient client) {
        client.close();
        streams.remove(client);
    }

    protected HttpResponse<String> postDriverEmergency(int driverIndex, String emergencyId) throws Exception {
        String token = new StreamTokens(jwtEncoder).mint("DRIVER", FIRST_DRIVER_USER + driverIndex, null,
            TOKEN_VALIDITY);
        String body = "{\"id\":\"" + emergencyId + "\",\"shiftId\":" + shifts.get(driverIndex).getId()
            + ",\"activatedAt\":\"" + Instant.now(clock).minusSeconds(30)
            + "\",\"latitude\":-12.046,\"longitude\":-77.042}";
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/driver-emergencies"))
            .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    protected HttpResponse<String> approve(long groupId, String bearer) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/request-groups/" + groupId + "/approve"))
            .header("Authorization", "Bearer " + bearer).POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    protected HttpResponse<String> reviewQueue(String bearer) throws Exception {
        return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/review-queue"))
            .header("Authorization", "Bearer " + bearer).GET().build());
    }

    protected PassengerRequestGroup awaitingGroup(int shiftIndex) {
        DriverShift shift = shifts.get(shiftIndex);
        PassengerRequestGroup group = PassengerRequestGroup.open(company.getId(), shift.getBusId(), shift.getId(),
            clock.instant());
        group.moveToAwaitingApproval(clock.instant());
        return groupRepository.saveAndFlush(group);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
