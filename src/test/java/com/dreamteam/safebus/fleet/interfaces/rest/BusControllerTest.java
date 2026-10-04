package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BusControllerTest {

    private static final String URL = "/api/v1/buses";

    @Autowired MockMvc mockMvc;
    @Autowired BusRepository busRepository;

    private org.springframework.test.web.servlet.request.RequestPostProcessor supervisorJwt() {
        return jwt()
            .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", 1L).subject("42"))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    @Test
    void createBus_validPlate_returns201WithFields() throws Exception {
        mockMvc.perform(post(URL).with(supervisorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"ABC-123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.plate").value("ABC-123"))
            .andExpect(jsonPath("$.qrCode").isString())
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void createBus_duplicatePlate_returns409PlateTaken() throws Exception {
        mockMvc.perform(post(URL).with(supervisorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"DUP-001\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post(URL).with(supervisorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"DUP-001\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLATE_TAKEN"));
    }

    @Test
    void createBus_missingPlate_returns422ValidationFailed() throws Exception {
        mockMvc.perform(post(URL).with(supervisorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createBus_driverRole_returns403() throws Exception {
        mockMvc.perform(post(URL)
                .with(jwt()
                    .jwt(b -> b.claim("role", "DRIVER").claim("companyId", 1L).subject("10"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"ROLE-403\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void createBus_passengerRole_returns403() throws Exception {
        mockMvc.perform(post(URL)
                .with(jwt()
                    .jwt(b -> b.claim("role", "PASSENGER").subject("20"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"PAX-403\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void createBus_noToken_returns401() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plate\":\"NOAUTH\"}"))
            .andExpect(status().isUnauthorized());
    }
}
