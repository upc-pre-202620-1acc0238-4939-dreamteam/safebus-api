package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmergencyAttentionControllerTest {

    private static final long COMPANY_ID         = 300L;
    private static final long OTHER_COMPANY_ID   = 301L;
    private static final long SUPERVISOR_USER_ID = 9000L;
    private static final long OTHER_SUPER_USER   = 9001L;

    @Autowired MockMvc              mockMvc;
    @Autowired EmergencyRepository  emergencyRepository;
    @Autowired Clock clock;

    Emergency active;

    @BeforeEach
    void setUp() {
        active = emergencyRepository.save(
            Emergency.activateByDriver(
                UUID.randomUUID().toString(),
                COMPANY_ID, 10L, 300L, 200L, 400L, null,
                Instant.now(clock).minusSeconds(60), clock));
    }

    @AfterEach
    void tearDown() {
        emergencyRepository.deleteAll();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor supervisorJwt(
            long userId, long companyId) {
        return jwt()
            .jwt(b -> b.subject(String.valueOf(userId))
                .claim("role", "SUPERVISOR")
                .claim("companyId", companyId))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private String startUrl() { return "/api/v1/emergencies/" + active.getId() + "/start-attention"; }
    private String closeUrl() { return "/api/v1/emergencies/" + active.getId() + "/close"; }
    private String closeBody(String outcome) {
        return "{\"outcome\":\"" + outcome + "\",\"response\":\"ok\"}";
    }

    // --- US10 S1: supervisor starts attention ---

    @Test
    void startAttention_valid_returns200InProgress() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(active.getId()))
            .andExpect(jsonPath("$.status").value(EmergencyStatus.IN_PROGRESS.name()))
            .andExpect(jsonPath("$.responsibleSupervisorId").value(SUPERVISOR_USER_ID))
            .andExpect(jsonPath("$.attentionStartedAt").isString());

        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void startAttention_twice_returns409InvalidTransition() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)))
            .andExpect(status().isOk());

        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        // status and supervisor unchanged
        Emergency reloaded = emergencyRepository.findById(active.getId()).orElseThrow();
        assertEquals(EmergencyStatus.IN_PROGRESS, reloaded.getStatus());
        assertEquals(SUPERVISOR_USER_ID, reloaded.getResponsibleSupervisorUserId());
    }

    @Test
    void startAttention_anotherCompany_returns403AccessDenied() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(OTHER_SUPER_USER, OTHER_COMPANY_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ACCESS_DENIED"));

        assertEquals(EmergencyStatus.ACTIVE,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void startAttention_nonExistentId_sameCodeAsAnotherCompany() throws Exception {
        mockMvc.perform(post("/api/v1/emergencies/" + active.getId() + "/start-attention")
                .with(supervisorJwt(OTHER_SUPER_USER, OTHER_COMPANY_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/emergencies/nonexistent-id/start-attention")
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ACCESS_DENIED"));
    }

    @Test
    void startAttention_driverRole_returns403() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(jwt().jwt(b -> b.claim("role", "DRIVER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void startAttention_passengerRole_returns403() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(jwt().jwt(b -> b.claim("role", "PASSENGER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void startAttention_noToken_returns401() throws Exception {
        mockMvc.perform(post(startUrl()))
            .andExpect(status().isUnauthorized());
    }

    // --- US10 S4: full lifecycle and close rejections ---

    @Test
    void close_validLifecycle_returnsClosed() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)))
            .andExpect(status().isOk());

        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeBody("all clear")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(active.getId()))
            .andExpect(jsonPath("$.status").value(EmergencyStatus.CLOSED.name()))
            .andExpect(jsonPath("$.closedAt").isString())
            .andExpect(jsonPath("$.outcome").value("all clear"));

        assertEquals(EmergencyStatus.CLOSED,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_fromActive_returns422AttentionNotStarted() throws Exception {
        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeBody("outcome")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ATTENTION_NOT_STARTED"));

        assertEquals(EmergencyStatus.ACTIVE,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_nullOutcome_returns422OutcomeRequired() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)));

        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":null,\"response\":null}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("OUTCOME_REQUIRED"));

        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_blankOutcome_returns422OutcomeRequired() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)));

        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"\",\"response\":null}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("OUTCOME_REQUIRED"));
    }

    @Test
    void close_outcomeTooLong_returns422OutcomeTooLong() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)));

        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + "x".repeat(501) + "\",\"response\":null}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("OUTCOME_TOO_LONG"));

        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_alreadyClosed_returns409InvalidTransition() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)));
        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON).content(closeBody("first outcome")));

        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON).content(closeBody("second")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        Emergency reloaded = emergencyRepository.findById(active.getId()).orElseThrow();
        assertEquals(EmergencyStatus.CLOSED, reloaded.getStatus());
        assertEquals("first outcome", reloaded.getOutcome());
    }

    @Test
    void close_anotherCompany_returns403() throws Exception {
        mockMvc.perform(post(startUrl())
                .with(supervisorJwt(SUPERVISOR_USER_ID, COMPANY_ID)));

        mockMvc.perform(post(closeUrl())
                .with(supervisorJwt(OTHER_SUPER_USER, OTHER_COMPANY_ID))
                .contentType(MediaType.APPLICATION_JSON).content(closeBody("outcome")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMERGENCY_ACCESS_DENIED"));

        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_driverRole_returns403() throws Exception {
        mockMvc.perform(post(closeUrl())
                .with(jwt().jwt(b -> b.claim("role", "DRIVER").subject("1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                .contentType(MediaType.APPLICATION_JSON).content(closeBody("outcome")))
            .andExpect(status().isForbidden());
    }

    @Test
    void close_noToken_returns401() throws Exception {
        mockMvc.perform(post(closeUrl())
                .contentType(MediaType.APPLICATION_JSON).content(closeBody("outcome")))
            .andExpect(status().isUnauthorized());
    }
}
