package com.dreamteam.safebus.fleetmonitoring.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.LocationEvent;
import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FleetOverviewControllerTest {

    private static final Instant NOW = Instant.parse("2030-09-01T12:00:00Z");
    private static final String URL = "/api/v1/fleet/overview";
    private static final String DRIVER_SECRET = "FM-DRV-QR-SECRET";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired VehicleLocationRepository vehicleLocationRepository;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired Clock clock;

    private Company company;
    private Company otherCompany;
    private Route route;
    private Driver driver;
    private Bus busA;
    private Bus busB;
    private DriverShift shiftA;
    private long nextAssignment = 8500L;
    private final List<String> emergencyIds = new ArrayList<>();
    private final List<DriverShift> shifts = new ArrayList<>();
    private final List<VehicleLocation> locations = new ArrayList<>();
    private final List<Bus> buses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Overview REST Company"));
        otherCompany = companyRepository.save(Company.create("Other Overview REST Company"));
        route = routeRepository.save(Route.create(company.getId(), "Line 7", "Central", "Airport"));
        driver = driverRepository.save(Driver.create(company.getId(), 8301L, "Rosa Driver",
            () -> DRIVER_SECRET, Duration.ofDays(365), clock));
        busA = saveBus(company, "FM-A");
        busB = saveBus(company, "FM-B");
        shiftA = activeShift(busA, NOW.minus(Duration.ofHours(1)));
    }

    @AfterEach
    void cleanUp() {
        emergencyRepository.deleteAllById(emergencyIds);
        driverShiftRepository.deleteAll(shifts);
        vehicleLocationRepository.deleteAll(locations);
        busRepository.deleteAll(buses);
        driverRepository.delete(driver);
        routeRepository.delete(route);
        companyRepository.deleteAll(List.of(company, otherCompany));
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private Bus saveBus(Company owner, String plate) {
        Bus b = busRepository.save(Bus.create(owner.getId(), plate, () -> "fm-qr-" + plate));
        buses.add(b);
        return b;
    }

    private DriverShift activeShift(Bus bus, Instant startedAt) {
        DriverShift s = driverShiftRepository.save(
            DriverShift.start(nextAssignment++, driver.getId(), bus.getId(), route.getId(), at(startedAt)));
        shifts.add(s);
        return s;
    }

    private void positionOf(Bus bus, double lat, double lon, double accuracy, Instant capturedAt) {
        VehicleLocation vl = VehicleLocation.empty(bus.getId());
        vl.applyIfNewer(LocationEvent.create("fm-" + bus.getId() + "-" + capturedAt, 1L, bus.getId(),
            lat, lon, accuracy, capturedAt, at(capturedAt)), at(capturedAt));
        locations.add(vehicleLocationRepository.save(vl));
    }

    private Emergency emergency(Company owner, Bus bus, Long shiftId, Instant receivedAt) {
        String id = UUID.randomUUID().toString();
        Emergency e = emergencyRepository.save(Emergency.activateByDriver(
            id, owner.getId(), driver.getId(), bus.getId(), shiftId, route.getId(), null,
            receivedAt.minusSeconds(5), at(receivedAt)));
        emergencyIds.add(id);
        return e;
    }

    private void startAttention(Emergency created, Instant now) {
        Emergency e = emergencyRepository.findById(created.getId()).orElseThrow();
        e.startAttention(99L, now);
        emergencyRepository.save(e);
    }

    private static RequestPostProcessor supervisorJwt(long companyId) {
        return jwt().jwt(b -> b.subject("9601").claim("role", "SUPERVISOR").claim("companyId", companyId))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private static RequestPostProcessor roleJwt(String role) {
        return jwt().jwt(b -> b.subject("9602").claim("role", role).claim("companyId", 1L))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String overviewBody() throws Exception {
        return mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private static Set<String> keys(String json, String path) {
        Map<String, Object> map = JsonPath.read(json, path);
        return map.keySet();
    }

    private void fullFixture() {
        busA.recordCapacity(40, "TECH-040", 1L, at(NOW));
        busRepository.save(busA);
        positionOf(busA, -12.05, -77.04, 8.5, NOW.minusSeconds(30));
        emergency(company, busA, shiftA.getId(), NOW.minus(Duration.ofMinutes(10)));
        Emergency attended = emergency(company, busA, shiftA.getId(), NOW.minus(Duration.ofMinutes(5)));
        startAttention(attended, NOW.minus(Duration.ofMinutes(4)));
    }

    // --- US11 S1: consult fleet and safety state ---

    @Test
    void s1_responseHasExactlyTheDocumentedKeys() throws Exception {
        fullFixture();

        String body = overviewBody();

        assertEquals(Set.of("generatedAt", "buses"), keys(body, "$"));
        assertEquals(Set.of("busId", "plate", "enabled", "capacity", "occupancy", "shift", "route", "driver",
            "location", "emergencies", "passengerGroups"), keys(body, "$.buses[0]"));
        assertEquals(Set.of("count", "capacity", "status"), keys(body, "$.buses[0].occupancy"));
        assertEquals(Set.of("status", "latitude", "longitude", "accuracyMeters", "capturedAt"),
            keys(body, "$.buses[0].location"));
        assertEquals(Set.of("id", "source", "priority", "status", "shiftId", "activatedAt", "receivedAt",
            "attentionStartedAt"), keys(body, "$.buses[0].emergencies[0]"));
        assertEquals(Set.of("shiftId", "startedAt"), keys(body, "$.buses[0].shift"));
        assertEquals(Set.of("name", "origin", "destination"), keys(body, "$.buses[0].route"));
        assertEquals(Set.of("fullName"), keys(body, "$.buses[0].driver"));
    }

    @Test
    void s1_returnsBusRouteDriverShiftLocationAndEmergencies() throws Exception {
        fullFixture();

        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedAt").value("2030-09-01T12:00:00Z"))
            .andExpect(jsonPath("$.buses.length()").value(2))
            .andExpect(jsonPath("$.buses[0].busId").value(busA.getId()))
            .andExpect(jsonPath("$.buses[0].plate").value("FM-A"))
            .andExpect(jsonPath("$.buses[0].enabled").value(true))
            .andExpect(jsonPath("$.buses[0].shift.shiftId").value(shiftA.getId()))
            .andExpect(jsonPath("$.buses[0].shift.startedAt").value("2030-09-01T11:00:00Z"))
            .andExpect(jsonPath("$.buses[0].route.name").value("Line 7"))
            .andExpect(jsonPath("$.buses[0].route.origin").value("Central"))
            .andExpect(jsonPath("$.buses[0].route.destination").value("Airport"))
            .andExpect(jsonPath("$.buses[0].driver.fullName").value("Rosa Driver"))
            .andExpect(jsonPath("$.buses[0].location.status").value("CURRENT"))
            .andExpect(jsonPath("$.buses[0].location.latitude").value(-12.05))
            .andExpect(jsonPath("$.buses[0].location.longitude").value(-77.04))
            .andExpect(jsonPath("$.buses[0].location.accuracyMeters").value(8.5))
            .andExpect(jsonPath("$.buses[0].location.capturedAt").value("2030-09-01T11:59:30Z"))
            .andExpect(jsonPath("$.buses[0].emergencies.length()").value(2))
            .andExpect(jsonPath("$.buses[0].emergencies[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.buses[0].emergencies[0].source").value("DRIVER"))
            .andExpect(jsonPath("$.buses[0].emergencies[0].priority").value("CRITICAL"))
            .andExpect(jsonPath("$.buses[0].emergencies[0].shiftId").value(shiftA.getId()))
            .andExpect(jsonPath("$.buses[0].emergencies[0].attentionStartedAt").value("2030-09-01T11:56:00Z"))
            .andExpect(jsonPath("$.buses[0].emergencies[1].status").value("ACTIVE"))
            .andExpect(jsonPath("$.buses[0].emergencies[1].attentionStartedAt").doesNotExist());
    }

    @Test
    void s1_busWithoutShiftPositionOrEmergenciesHasNullsAndEmptyLists() throws Exception {
        fullFixture();

        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses[1].plate").value("FM-B"))
            .andExpect(jsonPath("$.buses[1].shift").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[1].route").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[1].driver").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[1].location.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.buses[1].emergencies.length()").value(0));
    }

    @Test
    void s1_capacityComesFromUs12AndOccupancyCountIsNullWithStatusUnavailable() throws Exception {
        fullFixture();

        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses[0].capacity").value(40))
            .andExpect(jsonPath("$.buses[0].occupancy.capacity").value(40))
            .andExpect(jsonPath("$.buses[0].occupancy.count").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[0].occupancy.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.buses[1].capacity").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[1].occupancy.capacity").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[1].occupancy.count").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[1].occupancy.status").value("UNAVAILABLE"));
    }

    @Test
    void s1_passengerGroupsAreAlwaysEmpty() throws Exception {
        fullFixture();

        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses[0].passengerGroups.length()").value(0))
            .andExpect(jsonPath("$.buses[1].passengerGroups.length()").value(0));
    }

    @Test
    void s1_bodyNeverContainsSensitiveFieldsOrValues() throws Exception {
        fullFixture();

        String body = overviewBody().toLowerCase();

        for (String forbidden : List.of("qrcredential", "useraccountid", "loginid", "dni", "outcome",
            "userresponse", "responsiblesupervisor", "faceimage", DRIVER_SECRET.toLowerCase())) {
            assertFalse(body.contains(forbidden), "body must not contain " + forbidden);
        }
    }

    // --- US11 S2: identify outdated location ---

    private String locationStatusOfBusA(String body) {
        return JsonPath.read(body, "$.buses[0].location.status");
    }

    @Test
    void s2_positionTwoMinutesFiftyNineSecondsOld_isCurrent() throws Exception {
        positionOf(busA, -12.0, -77.0, 5.0, NOW.minusSeconds(179));

        assertEquals("CURRENT", locationStatusOfBusA(overviewBody()));
    }

    @Test
    void s2_positionExactlyThreeMinutesOld_isCurrent() throws Exception {
        positionOf(busA, -12.0, -77.0, 5.0, NOW.minusSeconds(180));

        assertEquals("CURRENT", locationStatusOfBusA(overviewBody()));
    }

    @Test
    void s2_positionThreeMinutesAndOneSecondOld_isStaleAndKeepsCoordinatesAndCaptureTime() throws Exception {
        positionOf(busA, -12.34, -77.56, 6.5, NOW.minusSeconds(181));

        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses[0].location.status").value("STALE"))
            .andExpect(jsonPath("$.buses[0].location.latitude").value(-12.34))
            .andExpect(jsonPath("$.buses[0].location.longitude").value(-77.56))
            .andExpect(jsonPath("$.buses[0].location.accuracyMeters").value(6.5))
            .andExpect(jsonPath("$.buses[0].location.capturedAt").value("2030-09-01T11:56:59Z"));
    }

    @Test
    void s2_busWithoutPosition_isUnavailableWithEveryOtherLocationFieldNull() throws Exception {
        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses[0].location.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.buses[0].location.latitude").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[0].location.longitude").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[0].location.accuracyMeters").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.buses[0].location.capturedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void s2_positionFromAPreviousShiftIsClassifiedByItsAgeLikeAnyOther() throws Exception {
        // the current shift started one hour ago; the sample was captured before that, so it is old
        positionOf(busA, -12.0, -77.0, 5.0, NOW.minus(Duration.ofHours(2)));

        assertEquals("STALE", locationStatusOfBusA(overviewBody()));
    }

    @Test
    void s2_positionCapturedBeforeTheCurrentShiftButWithinThreeMinutes_isCurrent() throws Exception {
        DriverShift fresh = activeShift(busB, NOW.minusSeconds(60));
        positionOf(busB, -12.0, -77.0, 5.0, NOW.minusSeconds(120));

        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses[1].shift.shiftId").value(fresh.getId()))
            .andExpect(jsonPath("$.buses[1].location.status").value("CURRENT"));
    }

    // --- isolation and roles ---

    @Test
    void supervisorOfAnotherCompany_seesOnlyHisOwnBusesAndEmergencies() throws Exception {
        Bus foreign = saveBus(otherCompany, "FM-X");
        emergency(otherCompany, foreign, 1L, NOW.minusSeconds(60));
        emergency(company, busA, shiftA.getId(), NOW.minusSeconds(120));

        mockMvc.perform(get(URL).with(supervisorJwt(otherCompany.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses.length()").value(1))
            .andExpect(jsonPath("$.buses[0].busId").value(foreign.getId()))
            .andExpect(jsonPath("$.buses[0].emergencies.length()").value(1));

        mockMvc.perform(get(URL).with(supervisorJwt(company.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses.length()").value(2))
            .andExpect(jsonPath("$.buses[?(@.plate=='FM-X')]").isEmpty())
            .andExpect(jsonPath("$.buses[0].emergencies.length()").value(1));
    }

    @Test
    void companyWithoutBuses_returnsAnEmptyList() throws Exception {
        mockMvc.perform(get(URL).with(supervisorJwt(otherCompany.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buses.length()").value(0));
    }

    @Test
    void supervisorWithoutCompany_isForbiddenWithFleetAccessDenied() throws Exception {
        RequestPostProcessor noCompany = jwt()
            .jwt(b -> b.subject("9603").claim("role", "SUPERVISOR"))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));

        mockMvc.perform(get(URL).with(noCompany))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FLEET_ACCESS_DENIED"))
            .andExpect(jsonPath("$.detail").value("access denied"));
    }

    @Test
    void driver_isForbidden() throws Exception {
        mockMvc.perform(get(URL).with(roleJwt("DRIVER"))).andExpect(status().isForbidden());
    }

    @Test
    void passenger_isForbidden() throws Exception {
        mockMvc.perform(get(URL).with(roleJwt("PASSENGER"))).andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
