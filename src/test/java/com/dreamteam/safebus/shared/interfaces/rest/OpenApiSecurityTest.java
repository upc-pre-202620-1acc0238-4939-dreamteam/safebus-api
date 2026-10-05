package com.dreamteam.safebus.shared.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSecurityTest {

    @Autowired
    MockMvc mockMvc;

    private ResultActions apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void apiDocs_declaresTheBearerSchemeAsGlobalSecurityRequirement() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.components.securitySchemes").value(hasKey("bearerAuth")))
                .andExpect(jsonPath("$.security[0]").value(hasKey("bearerAuth")));
    }

    @Test
    void apiDocs_publicOperationsDeclareAnEmptySecurityArray() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/auth/sign-in'].post.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sign-in'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/passengers'].post.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/passengers'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/contact-requests'].post.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/contact-requests'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").isEmpty());
    }

    @Test
    void apiDocs_protectedOperationDoesNotOverrideTheGlobalRequirement() throws Exception {
        apiDocs().andExpect(jsonPath("$.paths['/api/v1/buses'].post.security").doesNotExist());
    }
}
