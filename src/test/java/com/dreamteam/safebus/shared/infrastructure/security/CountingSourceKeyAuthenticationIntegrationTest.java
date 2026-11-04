package com.dreamteam.safebus.shared.infrastructure.security;

import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import com.dreamteam.safebus.trip.domain.model.CountingSource;
import com.dreamteam.safebus.trip.domain.port.CountingSourceKeys;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CountingSourceKeyAuthenticationIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);
    private static final String KEY_HEADER = CountingSourceKeyFilter.HEADER;

    @Autowired MockMvc mockMvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired CountingSourceRepository sourceRepository;
    @Autowired CountingSourceKeys keys;

    private Company company;
    private CountingSource source;
    private String rawKey;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Device Auth Company"));
        CountingSourceKeys.GeneratedKey generated = keys.generate();
        rawKey = generated.rawKey();
        source = sourceRepository.save(
            CountingSource.register(company.getId(), 4_000_001L, "door", generated.hash(), 1L, CLOCK));
    }

    @AfterEach
    void cleanUp() {
        sourceRepository.deleteAll();
        companyRepository.delete(company);
    }

    @Test
    void aValidKeyPassesAuthorizationOnADeviceRoute() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/occupancy-events").header(KEY_HEADER, rawKey)).andReturn();

        assertNotEquals(401, result.getResponse().getStatus());
        assertNotEquals(403, result.getResponse().getStatus());
    }

    @Test
    void aWrongKeyOnADeviceRouteGets403WithTheSharedDenial() throws Exception {
        mockMvc.perform(post("/api/v1/occupancy-events").header(KEY_HEADER, "sbk_wrong"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_SOURCE_NOT_AUTHORIZED"))
            .andExpect(jsonPath("$.detail").value("access denied"));
    }

    @Test
    void aRevokedKeyOnADeviceRouteGets403() throws Exception {
        source.revoke(CLOCK);
        sourceRepository.save(source);

        mockMvc.perform(post("/api/v1/occupancy-heartbeats").header(KEY_HEADER, rawKey))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_SOURCE_NOT_AUTHORIZED"));
    }

    @Test
    void aRotatedKeyStopsWorkingAndTheNewOneWorks() throws Exception {
        CountingSourceKeys.GeneratedKey next = keys.generate();
        source.rotate(next.hash(), CLOCK);
        sourceRepository.save(source);

        mockMvc.perform(post("/api/v1/occupancy-heartbeats").header(KEY_HEADER, rawKey))
            .andExpect(status().isForbidden());
        MvcResult result = mockMvc.perform(post("/api/v1/occupancy-heartbeats").header(KEY_HEADER, next.rawKey()))
            .andReturn();
        assertNotEquals(403, result.getResponse().getStatus());
        assertNotEquals(401, result.getResponse().getStatus());
    }

    @Test
    void noCredentialOnADeviceRouteGets401() throws Exception {
        mockMvc.perform(post("/api/v1/occupancy-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void aPassengerTokenOnADeviceRouteGets403() throws Exception {
        mockMvc.perform(post("/api/v1/occupancy-events")
                .with(jwt().jwt(b -> b.subject("5").claim("role", "PASSENGER"))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void theKeyAloneOnAnyOtherRouteGets401() throws Exception {
        mockMvc.perform(get("/api/v1/fleet/overview").header(KEY_HEADER, rawKey))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/passengers/me/anything").header(KEY_HEADER, rawKey))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aDevicePrincipalIsForbiddenOnANonDeviceRoute() throws Exception {
        DeviceAuthenticationToken token = new DeviceAuthenticationToken(
            new CountingSourcePrincipal(source.getId(), source.getBusId(), company.getId()));

        mockMvc.perform(get("/api/v1/fleet/overview").with(authentication(token)))
            .andExpect(status().isForbidden());
    }

    @Test
    void theDenialBodyNeverEchoesTheKey() throws Exception {
        String body = mockMvc.perform(post("/api/v1/occupancy-events").header(KEY_HEADER, "sbk_secret_value"))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("sbk_secret_value"));
    }
}
