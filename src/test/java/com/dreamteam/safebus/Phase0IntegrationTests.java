package com.dreamteam.safebus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase0IntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_returns200_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedPath_withoutToken_returns401Or403() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test-exceptions/not-found"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertTrue(status == 401 || status == 403, "Expected 401 or 403 but got " + status);
    }

    @Test
    void notFound_returns404_withCode() throws Exception {
        mockMvc.perform(get("/api/v1/test-exceptions/not-found").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void conflict_returns409_withCode() throws Exception {
        mockMvc.perform(get("/api/v1/test-exceptions/conflict").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void ruleViolation_returns422_withCode() throws Exception {
        mockMvc.perform(get("/api/v1/test-exceptions/rule-violation").with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RULE_VIOLATION"));
    }

    @Test
    void forbidden_returns403_withCode() throws Exception {
        mockMvc.perform(get("/api/v1/test-exceptions/forbidden").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
    }

    @Test
    void customCode_returns422_withCallerSuppliedCode() throws Exception {
        mockMvc.perform(get("/api/v1/test-exceptions/custom-code").with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void validationFailure_returns422_withValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/test-exceptions/validated")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void apiDocs_returns200_withOpenapiBody() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi")));
    }

    @Test
    void swaggerUi_isNotUnauthorized() throws Exception {
        MvcResult result = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertTrue(status != 401 && status != 403,
                "Expected swagger-ui.html to not return 401 or 403, but got " + status);
    }
}
