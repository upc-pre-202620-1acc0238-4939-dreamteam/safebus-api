package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.shared.application.CountingSourceKeyAuthenticator;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.ConsistencyState;
import com.dreamteam.safebus.trip.domain.model.CountingSource;
import com.dreamteam.safebus.trip.domain.model.CountingSourceStatus;
import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;
import com.dreamteam.safebus.trip.domain.port.CountingSourceKeys;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CountingSourceControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired CountingSourceRepository sourceRepository;
    @Autowired BusOccupancyRepository occupancyRepository;
    @Autowired CountingSourceKeys keys;
    @Autowired CountingSourceKeyAuthenticator authenticator;

    private Company company;
    private Company otherCompany;
    private Bus bus;
    private Bus otherBus;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = companyRepository.save(Company.create("CS Company " + suffix));
        otherCompany = companyRepository.save(Company.create("CS Other " + suffix));
        bus = busRepository.save(Bus.create(company.getId(), "CS-" + suffix, () -> "cs-qr-" + suffix));
        otherBus = busRepository.save(Bus.create(otherCompany.getId(), "CO-" + suffix, () -> "co-qr-" + suffix));
    }

    @AfterEach
    void cleanUp() {
        for (Bus b : List.of(bus, otherBus)) {
            sourceRepository.deleteAll(sourceRepository.findByBusIdOrderByIdAsc(b.getId()));
            occupancyRepository.findByBusId(b.getId()).ifPresent(occupancyRepository::delete);
            busRepository.delete(b);
        }
        companyRepository.delete(company);
        companyRepository.delete(otherCompany);
    }

    private RequestPostProcessor supervisor() {
        return jwt().jwt(b -> b.subject("42").claim("role", "SUPERVISOR").claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private RequestPostProcessor role(String role) {
        return jwt().jwt(b -> b.subject("43").claim("role", role).claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String base(Bus b) {
        return "/api/v1/buses/" + b.getId() + "/counting-sources";
    }

    private String register(String label) throws Exception {
        return mockMvc.perform(post(base(bus)).with(supervisor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"" + label + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private BusOccupancy countingOccupancy(int entries) {
        BusOccupancy o = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        o.initialize(UUID.randomUUID().toString(), 77L, 0, CLOCK.instant(), CLOCK.instant());
        for (int i = 1; i <= entries; i++) {
            o.apply(77L, OccupancyEventType.ENTRY, i, CLOCK.instant());
        }
        return occupancyRepository.saveAndFlush(o);
    }

    @Test
    void registerReturns201WithTheKeyOnceAndStoresOnlyItsHash() throws Exception {
        String body = register("front door");

        String rawKey = JsonPath.read(body, "$.key");
        assertTrue(rawKey.startsWith("sbk_"));
        Long sourceId = ((Number) JsonPath.read(body, "$.sourceId")).longValue();
        CountingSource stored = sourceRepository.findById(sourceId).orElseThrow();
        assertEquals(CountingSourceStatus.ACTIVE, stored.getStatus());
        assertEquals("ACTIVE", JsonPath.read(body, "$.status"));
        assertEquals("front door", JsonPath.read(body, "$.label"));
        assertTrue(sourceRepository.findByKeyHashAndStatus(keys.hash(rawKey), CountingSourceStatus.ACTIVE).isPresent());
        assertFalse(body.contains(keys.hash(rawKey)));
        assertTrue(authenticator.authenticate(rawKey).isPresent());
    }

    @Test
    void registerSendsNoStoreAndCreatesAnUninitializedOccupancy() throws Exception {
        mockMvc.perform(post(base(bus)).with(supervisor()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"));

        BusOccupancy occupancy = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        assertEquals(ConsistencyState.NOT_INITIALIZED, occupancy.getConsistency());
        assertNull(occupancy.getCount());
        assertEquals(company.getId(), occupancy.getCompanyId());
    }

    @Test
    void aSecondActiveSourceForTheSameBusConflicts() throws Exception {
        register("a");

        mockMvc.perform(post(base(bus)).with(supervisor()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ACTIVE_SOURCE_EXISTS"));
    }

    @Test
    void aLabelOver60CharactersIs422() throws Exception {
        mockMvc.perform(post(base(bus)).with(supervisor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"" + "x".repeat(61) + "\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("LABEL_TOO_LONG"));
        assertTrue(sourceRepository.findByBusIdOrderByIdAsc(bus.getId()).isEmpty());
    }

    @Test
    void foreignAndMissingBusesGetTheSame403() throws Exception {
        String foreign = mockMvc.perform(post(base(otherBus)).with(supervisor()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(post("/api/v1/buses/987654321/counting-sources").with(supervisor()))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertEquals(JsonPath.<String>read(foreign, "$.detail"), JsonPath.<String>read(missing, "$.detail"));
        assertEquals(JsonPath.<String>read(foreign, "$.code"), JsonPath.<String>read(missing, "$.code"));
        assertTrue(sourceRepository.findByBusIdOrderByIdAsc(otherBus.getId()).isEmpty());
    }

    @Test
    void passengersAndDriversGet403AndAnonymousGets401() throws Exception {
        for (String role : List.of("PASSENGER", "DRIVER")) {
            mockMvc.perform(post(base(bus)).with(role(role))).andExpect(status().isForbidden());
            mockMvc.perform(get(base(bus)).with(role(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(base(bus))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base(bus))).andExpect(status().isUnauthorized());
    }

    @Test
    void listNeverContainsTheKeyOrItsHash() throws Exception {
        String created = register("door");
        String rawKey = JsonPath.read(created, "$.key");

        String body = mockMvc.perform(get(base(bus)).with(supervisor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(rawKey));
        assertFalse(body.contains(keys.hash(rawKey)));
        assertFalse(body.toLowerCase().contains("key"));
        assertFalse(body.contains("sbk_"));
    }

    @Test
    void listOfAForeignBusIs403() throws Exception {
        mockMvc.perform(get(base(otherBus)).with(supervisor()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));
    }

    @Test
    void rotationReturnsANewKeyAndTheOldOneStopsWorking() throws Exception {
        String created = register("door");
        String oldKey = JsonPath.read(created, "$.key");
        long sourceId = ((Number) JsonPath.read(created, "$.sourceId")).longValue();

        String rotated = mockMvc.perform(post(base(bus) + "/" + sourceId + "/rotation").with(supervisor()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsString();

        String newKey = JsonPath.read(rotated, "$.key");
        assertNotEquals(oldKey, newKey);
        assertTrue(authenticator.authenticate(oldKey).isEmpty());
        assertTrue(authenticator.authenticate(newKey).isPresent());
        assertFalse(rotated.contains(keys.hash(newKey)));
        assertFalse(mockMvc.perform(get(base(bus)).with(supervisor())).andReturn().getResponse()
            .getContentAsString().contains(newKey));
    }

    @Test
    void rotationKeepsTheCountSequenceAndConsistency() throws Exception {
        String created = register("door");
        long sourceId = ((Number) JsonPath.read(created, "$.sourceId")).longValue();
        BusOccupancy before = countingOccupancy(3);

        mockMvc.perform(post(base(bus) + "/" + sourceId + "/rotation").with(supervisor()))
            .andExpect(status().isOk());

        BusOccupancy after = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        assertEquals(3, after.getCount());
        assertEquals(3L, after.getLastSequence());
        assertEquals(ConsistencyState.CONSISTENT, after.getConsistency());
        assertEquals(before.getEpoch(), after.getEpoch());
        assertEquals(before.getShiftId(), after.getShiftId());
        assertEquals(sourceId, after.getSourceId());
    }

    @Test
    void rotatingAnUnknownOrForeignBusSourceIs404() throws Exception {
        register("door");
        CountingSource foreign = sourceRepository.save(CountingSource.register(
            otherCompany.getId(), otherBus.getId(), "x", keys.generate().hash(), 1L, CLOCK));

        mockMvc.perform(post(base(bus) + "/987654321/rotation").with(supervisor()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
        mockMvc.perform(post(base(bus) + "/" + foreign.getId() + "/rotation").with(supervisor()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
    }

    @Test
    void rotatingARevokedSourceConflicts() throws Exception {
        long sourceId = ((Number) JsonPath.read(register("door"), "$.sourceId")).longValue();
        mockMvc.perform(post(base(bus) + "/" + sourceId + "/revocation").with(supervisor()))
            .andExpect(status().isOk());

        mockMvc.perform(post(base(bus) + "/" + sourceId + "/rotation").with(supervisor()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SOURCE_REVOKED"));
    }

    @Test
    void rotatingOnAForeignBusIs403() throws Exception {
        mockMvc.perform(post(base(otherBus) + "/1/rotation").with(supervisor()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));
    }

    @Test
    void revocationStopsTheKeyAndIsIdempotent() throws Exception {
        String created = register("door");
        String rawKey = JsonPath.read(created, "$.key");
        long sourceId = ((Number) JsonPath.read(created, "$.sourceId")).longValue();

        String first = mockMvc.perform(post(base(bus) + "/" + sourceId + "/revocation").with(supervisor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"))
            .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post(base(bus) + "/" + sourceId + "/revocation").with(supervisor()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"))
            .andReturn().getResponse().getContentAsString();

        assertEquals(JsonPath.<String>read(first, "$.revokedAt"), JsonPath.<String>read(second, "$.revokedAt"));
        assertTrue(authenticator.authenticate(rawKey).isEmpty());
        assertFalse(first.contains(rawKey));
    }

    @Test
    void revokingAnUnknownSourceIs404() throws Exception {
        mockMvc.perform(post(base(bus) + "/987654321/revocation").with(supervisor()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
    }

    @Test
    void revocationDoesNotTouchTheCount() throws Exception {
        long sourceId = ((Number) JsonPath.read(register("door"), "$.sourceId")).longValue();
        countingOccupancy(2);

        mockMvc.perform(post(base(bus) + "/" + sourceId + "/revocation").with(supervisor()))
            .andExpect(status().isOk());

        BusOccupancy after = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        assertEquals(2, after.getCount());
        assertEquals(ConsistencyState.CONSISTENT, after.getConsistency());
    }

    @Test
    void registeringAfterARevocationResetsTheCountAndStartsANewEpoch() throws Exception {
        long firstId = ((Number) JsonPath.read(register("old"), "$.sourceId")).longValue();
        BusOccupancy before = countingOccupancy(4);
        mockMvc.perform(post(base(bus) + "/" + firstId + "/revocation").with(supervisor()))
            .andExpect(status().isOk());

        String body = register("new");

        long secondId = ((Number) JsonPath.read(body, "$.sourceId")).longValue();
        BusOccupancy after = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        assertEquals(ConsistencyState.NOT_INITIALIZED, after.getConsistency());
        assertNull(after.getCount());
        assertNull(after.getShiftId());
        assertEquals(0L, after.getLastSequence());
        assertEquals(before.getEpoch() + 1, after.getEpoch());
        assertEquals(secondId, after.getSourceId());
        assertEquals(1, occupancyRepository.findAll().stream()
            .filter(o -> o.getBusId().equals(bus.getId())).count());
    }

    @Test
    void theRevokedSourceStaysListedAlongsideTheNewOne() throws Exception {
        long firstId = ((Number) JsonPath.read(register("old"), "$.sourceId")).longValue();
        mockMvc.perform(post(base(bus) + "/" + firstId + "/revocation").with(supervisor()));
        register("new");

        mockMvc.perform(get(base(bus)).with(supervisor()))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].status").value("REVOKED"))
            .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }
}
