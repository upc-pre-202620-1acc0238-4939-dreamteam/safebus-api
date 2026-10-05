package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusCapacityControllerTest {

    private static final Instant ORIGINAL_TIME = Instant.parse("2030-01-01T10:00:00.123Z");
    private static final Instant UPDATE_TIME = Instant.parse("2030-01-02T10:00:00.456789123Z");
    private static final Instant STORED_UPDATE_TIME = Instant.parse("2030-01-02T10:00:00.456Z");

    @Autowired MockMvc mockMvc;
    @Autowired BusRepository busRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean Clock clock;

    private Company company;
    private Company otherCompany;
    private Bus bus;
    private Bus foreignBus;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Capacity HTTP Company"));
        otherCompany = companyRepository.save(Company.create("Other Capacity HTTP Company"));
        bus = savedBus(company.getId(), "CAP-HTTP1", "capacity-http-qr1");
        foreignBus = savedBus(otherCompany.getId(), "CAP-HTTP2", "capacity-http-qr2");
        when(clock.instant()).thenReturn(UPDATE_TIME);
    }

    private Bus savedBus(Long companyId, String plate, String qr) {
        Bus created = Bus.create(companyId, plate, () -> qr);
        created.recordCapacity(30, "TECH-OLD", 7L, Clock.fixed(ORIGINAL_TIME, ZoneOffset.UTC));
        return busRepository.saveAndFlush(created);
    }

    @AfterEach
    void cleanUp() {
        busRepository.deleteById(bus.getId());
        busRepository.deleteById(foreignBus.getId());
        companyRepository.deleteById(company.getId());
        companyRepository.deleteById(otherCompany.getId());
    }

    private String url(Long id) {
        return "/api/v1/buses/" + id + "/capacity";
    }

    private RequestPostProcessor token(String role, Long userId, Long companyId) {
        return jwt().jwt(b -> b.subject(userId.toString()).claim("role", role).claim("companyId", companyId))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("capacity", 40);
        body.put("technicalRecordReference", "  TECH-NEW  ");
        return body;
    }

    private ResultActions submit(Long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(url(id)).with(token("SUPERVISOR", 42L, company.getId()))
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void s1_updateReturns200WithExactFieldsAndPersistsCapacityReferenceAuthorAndTime() throws Exception {
        submit(bus.getId(), validBody()).andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(5)))
            .andExpect(jsonPath("$.busId").value(bus.getId()))
            .andExpect(jsonPath("$.capacity").value(40))
            .andExpect(jsonPath("$.technicalRecordReference").value("TECH-NEW"))
            .andExpect(jsonPath("$.updatedByUserId").value(42))
            .andExpect(jsonPath("$.updatedAt").value(STORED_UPDATE_TIME.toString()));

        Bus stored = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(40, stored.getCapacity());
        assertEquals("TECH-NEW", stored.getCapacityReference());
        assertEquals(42L, stored.getCapacityUpdatedByUserId());
        assertEquals(STORED_UPDATE_TIME, stored.getCapacityUpdatedAt());
        assertBaseFields(bus, stored);
        assertUnchanged(foreignBus);
    }

    @Test
    void update_secondUpdateReplacesFirstCapacityReferenceAuthorAndTime() throws Exception {
        submit(bus.getId(), validBody()).andExpect(status().isOk());
        when(clock.instant()).thenReturn(UPDATE_TIME.plusSeconds(60));
        Map<String, Object> second = validBody();
        second.put("capacity", 55);
        second.put("technicalRecordReference", "TECH-SECOND");

        mockMvc.perform(put(url(bus.getId())).with(token("SUPERVISOR", 99L, company.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(second)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(55))
            .andExpect(jsonPath("$.technicalRecordReference").value("TECH-SECOND"))
            .andExpect(jsonPath("$.updatedByUserId").value(99))
            .andExpect(jsonPath("$.updatedAt").value(STORED_UPDATE_TIME.plusSeconds(60).toString()));

        Bus stored = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(55, stored.getCapacity());
        assertEquals("TECH-SECOND", stored.getCapacityReference());
        assertEquals(99L, stored.getCapacityUpdatedByUserId());
        assertEquals(STORED_UPDATE_TIME.plusSeconds(60), stored.getCapacityUpdatedAt());
        assertBaseFields(bus, stored);
    }

    @Test
    void s2_zeroCapacityReturns422AndPreservesLastValidCapacity() throws Exception {
        assertInvalidCapacity(0);
    }

    @Test
    void s2_negativeCapacityReturns422AndPreservesLastValidCapacity() throws Exception {
        assertInvalidCapacity(-3);
    }

    @Test
    void s2_fractionalCapacityReturns422WithoutTruncatingAndPreservesLastValidCapacity() throws Exception {
        assertInvalidCapacity(new BigDecimal("12.5"));
    }

    private void assertInvalidCapacity(Number value) throws Exception {
        Map<String, Object> body = validBody();
        body.put("capacity", value);
        assertRejected(body, "INVALID_CAPACITY", "capacity");
    }

    @Test
    void s2_missingCapacityReturns422AndPreservesLastValidCapacity() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("capacity");
        assertRejected(body, "CAPACITY_REQUIRED", "capacity");
    }

    @Test
    void s2_blankReferenceReturns422AndPreservesLastValidCapacity() throws Exception {
        Map<String, Object> body = validBody();
        body.put("technicalRecordReference", "  ");
        assertRejected(body, "CAPACITY_REFERENCE_REQUIRED", "technicalRecordReference");
    }

    @Test
    void s2_missingReferenceReturns422AndPreservesLastValidCapacity() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("technicalRecordReference");
        assertRejected(body, "CAPACITY_REFERENCE_REQUIRED", "technicalRecordReference");
    }

    @Test
    void update_referenceOf101CharactersReturns422AndPreservesLastValidCapacity() throws Exception {
        Map<String, Object> body = validBody();
        body.put("technicalRecordReference", "A".repeat(101));
        assertRejected(body, "CAPACITY_REFERENCE_TOO_LONG", "technicalRecordReference");
    }

    @Test
    void update_capacityAboveIntegerMaxReturns422AndPreservesLastValidCapacity() throws Exception {
        assertInvalidCapacity((long) Integer.MAX_VALUE + 1);
    }

    @Test
    void update_referenceValidationPrecedesMissingCapacity() throws Exception {
        assertRejected(new LinkedHashMap<>(), "CAPACITY_REFERENCE_REQUIRED", "technicalRecordReference");
    }

    static Stream<Arguments> validCapacities() {
        return Stream.of(Arguments.of(1, 1), Arguments.of(new BigDecimal("12.0"), 12),
            Arguments.of(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @MethodSource("validCapacities")
    void update_acceptsIntegerBoundariesAndDecimalWithoutFraction(Number value, int expected) throws Exception {
        Map<String, Object> body = validBody();
        body.put("capacity", value);
        body.put("technicalRecordReference", " " + "R".repeat(100) + " ");
        submit(bus.getId(), body).andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(expected));
        Bus stored = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(expected, stored.getCapacity());
        assertEquals("R".repeat(100), stored.getCapacityReference());
    }

    private void assertRejected(Map<String, Object> body, String code, String field) throws Exception {
        long count = busRepository.count();
        submit(bus.getId(), body).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.detail", containsString(field)));

        assertEquals(count, busRepository.count());
        assertUnchanged(bus);
        assertUnchanged(foreignBus);
    }

    private void assertUnchanged(Bus before) {
        Bus after = busRepository.findById(before.getId()).orElseThrow();
        assertBaseFields(before, after);
        assertEquals(before.getCapacity(), after.getCapacity());
        assertEquals(before.getCapacityReference(), after.getCapacityReference());
        assertEquals(before.getCapacityUpdatedByUserId(), after.getCapacityUpdatedByUserId());
        assertEquals(before.getCapacityUpdatedAt(), after.getCapacityUpdatedAt());
    }

    private void assertBaseFields(Bus before, Bus after) {
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getCompanyId(), after.getCompanyId());
        assertEquals(before.getPlate(), after.getPlate());
        assertEquals(before.getQrCode(), after.getQrCode());
        assertEquals(before.isEnabled(), after.isEnabled());
    }
}
