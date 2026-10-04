package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriverControllerTest {

    private static final String URL = "/api/v1/drivers";

    @Autowired MockMvc mockMvc;
    @Autowired DriverRepository driverRepository;
    @Autowired UserAccountRepository userAccountRepository;

    @AfterEach
    void tearDown() {
        driverRepository.deleteAll();
        for (String id : new String[]{"drv-ctrl-ok", "drv-ctrl-short", "drv-ctrl-dup"}) {
            userAccountRepository.findByLoginId(id).ifPresent(userAccountRepository::delete);
        }
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor supervisorJwt(long companyId) {
        return jwt()
            .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", companyId).subject("42"))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    @Test
    void createDriver_valid_returns201WithQrCredential() throws Exception {
        mockMvc.perform(post(URL).with(supervisorJwt(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Test Driver\",\"loginId\":\"drv-ctrl-ok\",\"initialPassword\":\"Safebus2024!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.fullName").value("Test Driver"))
            .andExpect(jsonPath("$.loginId").value("drv-ctrl-ok"))
            .andExpect(jsonPath("$.qrCredential").isString())
            .andExpect(jsonPath("$.qrCredentialExpiresAt").isString());
    }

    @Test
    void createDriver_shortPassword_returns422AndNoDriverRow() throws Exception {
        long before = driverRepository.count();

        mockMvc.perform(post(URL).with(supervisorJwt(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Bad PW\",\"loginId\":\"drv-ctrl-short\",\"initialPassword\":\"short\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));

        assertEquals(before, driverRepository.count());
    }

    @Test
    void createDriver_duplicateLoginId_returns409AndNoExtraDriver() throws Exception {
        mockMvc.perform(post(URL).with(supervisorJwt(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"First\",\"loginId\":\"drv-ctrl-dup\",\"initialPassword\":\"Safebus2024!\"}"))
            .andExpect(status().isCreated());

        long afterFirst = driverRepository.count();

        mockMvc.perform(post(URL).with(supervisorJwt(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Second\",\"loginId\":\"drv-ctrl-dup\",\"initialPassword\":\"Safebus2024!\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"));

        assertEquals(afterFirst, driverRepository.count());
    }

    @Test
    void createDriver_missingFields_returns422ValidationFailed() throws Exception {
        mockMvc.perform(post(URL).with(supervisorJwt(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"\",\"loginId\":\"\",\"initialPassword\":\"\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createDriver_driverRole_returns403() throws Exception {
        mockMvc.perform(post(URL)
                .with(jwt()
                    .jwt(b -> b.claim("role", "DRIVER").claim("companyId", 1L).subject("10"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"X\",\"loginId\":\"x\",\"initialPassword\":\"Safebus2024!\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void createDriver_noToken_returns401() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"X\",\"loginId\":\"x\",\"initialPassword\":\"Safebus2024!\"}"))
            .andExpect(status().isUnauthorized());
    }
}
