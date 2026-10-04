package com.dreamteam.safebus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void supervisorOnlyEndpoint_withDriverRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/test-security/supervisor-only")
                        .with(jwt()
                                .jwt(b -> b.claim("role", "DRIVER").claim("companyId", 1L).subject("10"))
                                .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorOnlyEndpoint_withSupervisorRole_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/test-security/supervisor-only")
                        .with(jwt()
                                .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", 1L).subject("42"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void companyResource_supervisorAccessingOtherCompany_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/test-security/company/2")
                        .with(jwt()
                                .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", 1L).subject("42"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyResource_supervisorAccessingOwnCompany_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/test-security/company/1")
                        .with(jwt()
                                .jwt(b -> b.claim("role", "SUPERVISOR").claim("companyId", 1L).subject("42"))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflight_toProtectedPath_returns200WithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/v1/auth/sign-out")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
