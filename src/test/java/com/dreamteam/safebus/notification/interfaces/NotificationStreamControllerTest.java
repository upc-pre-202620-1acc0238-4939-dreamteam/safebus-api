package com.dreamteam.safebus.notification.interfaces;

import com.dreamteam.safebus.notification.application.SubscriptionRegistry;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.SupervisorEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NotificationStreamControllerTest {

    private static final String URL = "/api/v1/notifications/stream";

    @Autowired MockMvc mockMvc;
    @Autowired SubscriptionRegistry registry;
    @Autowired NotificationContextFacade facade;

    @AfterEach
    void closeStreams() {
        registry.all().forEach(registry::remove);
    }

    private RequestPostProcessor token(String role, long userId, Long companyId) {
        return jwt().jwt(b -> {
            b.subject(String.valueOf(userId)).claim("role", role);
            if (companyId != null) {
                b.claim("companyId", companyId);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private MvcResult open(long userId, long companyId) throws Exception {
        return mockMvc.perform(get(URL).with(token("SUPERVISOR", userId, companyId)))
            .andExpect(request().asyncStarted())
            .andReturn();
    }

    private static boolean waitFor(MvcResult result, String text) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (result.getResponse().getContentAsString().contains(text)) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    @Test
    void streamWithoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void streamAsPassengerOrDriver_returns403() throws Exception {
        mockMvc.perform(get(URL).with(token("PASSENGER", 91001L, 91L))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(token("DRIVER", 91002L, 91L))).andExpect(status().isForbidden());
        assertEquals(0, registry.all().size());
    }

    @Test
    void streamWithoutCompanyInToken_returns403() throws Exception {
        mockMvc.perform(get(URL).with(token("SUPERVISOR", 91003L, null)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_SCOPE_DENIED"));
        assertEquals(0, registry.all().size());
    }

    @Test
    void streamNamingForeignCompany_returns403AndRegistersNothing() throws Exception {
        mockMvc.perform(get(URL).param("companyId", "92").with(token("SUPERVISOR", 91004L, 91L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_SCOPE_DENIED"));
        assertEquals(0, registry.all().size());
    }

    @Test
    void streamNamingOwnCompany_opens() throws Exception {
        mockMvc.perform(get(URL).param("companyId", "91").with(token("SUPERVISOR", 91005L, 91L)))
            .andExpect(request().asyncStarted());
        assertEquals(1, registry.count(91L));
    }

    @Test
    void openStream_sendsRetryAndConnectedComment() throws Exception {
        MvcResult stream = open(91006L, 91L);

        String body = stream.getResponse().getContentAsString();
        assertTrue(body.contains("retry:3000"), body);
        assertTrue(body.contains(":connected"), body);
    }

    @Test
    void openStream_sendsCacheAndProxyHeaders() throws Exception {
        MvcResult stream = open(91007L, 91L);

        assertEquals("no-cache, no-transform", stream.getResponse().getHeader("Cache-Control"));
        assertEquals("no", stream.getResponse().getHeader("X-Accel-Buffering"));
        assertTrue(stream.getResponse().getContentType().startsWith("text/event-stream"));
    }

    @Test
    void publishedEvent_reachesOnlyTheSubscribersOfItsCompany() throws Exception {
        MvcResult own = open(91008L, 91L);
        MvcResult foreign = open(91009L, 92L);

        facade.publish(() -> Optional.of(new SupervisorEvent(91L, NotificationContextFacade.EMERGENCY_ACTIVATED,
            "evt-own", Map.of("eventId", "evt-own"))));

        assertTrue(waitFor(own, "id:evt-own"), own.getResponse().getContentAsString());
        assertTrue(own.getResponse().getContentAsString().contains("event:EmergencyActivated"));
        Thread.sleep(200);
        assertFalse(foreign.getResponse().getContentAsString().contains("evt-own"));
    }

    @Test
    void fourthStreamOfOneUser_returns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            open(91010L, 91L);
        }

        mockMvc.perform(get(URL).with(token("SUPERVISOR", 91010L, 91L)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("SUBSCRIPTION_LIMIT_USER"));
        assertEquals(3, registry.count(91L));
    }
}
