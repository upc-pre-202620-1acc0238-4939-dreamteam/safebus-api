package com.dreamteam.safebus.notification.interfaces;

import com.dreamteam.safebus.notification.application.SubscriptionRegistry;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.SupervisorEvent;
import com.dreamteam.safebus.notification.support.SseTestClient;
import com.dreamteam.safebus.notification.support.StreamTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "safebus.scheduling.enabled=true",
    "safebus.notifications.keep-alive=PT0.3S"
})
class NotificationStreamServerTest {

    private static final String PATH = "/api/v1/notifications/stream";

    @LocalServerPort int port;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired SubscriptionRegistry registry;
    @Autowired NotificationContextFacade facade;

    private final List<SseTestClient> clients = new ArrayList<>();

    @AfterEach
    void closeClients() {
        clients.forEach(SseTestClient::close);
    }

    private StreamTokens tokens() {
        return new StreamTokens(jwtEncoder);
    }

    private SseTestClient connect(String path, String bearer) throws Exception {
        SseTestClient client = SseTestClient.connect("localhost", port, path, bearer);
        clients.add(client);
        return client;
    }

    @Test
    void expiredToken_returns401() throws Exception {
        SseTestClient client = connect(PATH, tokens().supervisor(95001L, 97L, Duration.ofMinutes(-5)));

        assertEquals(401, client.status());
        assertEquals(0, registry.count(97L));
    }

    @Test
    void malformedAndMissingToken_return401() throws Exception {
        assertEquals(401, connect(PATH, "not-a-jwt").status());
        assertEquals(401, connect(PATH, null).status());
    }

    @Test
    void passengerToken_returns403() throws Exception {
        SseTestClient client = connect(PATH, tokens().mint("PASSENGER", 95002L, 95L, Duration.ofMinutes(5)));

        assertEquals(403, client.status());
    }

    @Test
    void foreignCompany_returns403AndNothingIsRegistered() throws Exception {
        SseTestClient client = connect(PATH + "?companyId=94", tokens().supervisor(95003L, 98L, Duration.ofMinutes(5)));

        assertEquals(403, client.status());
        assertEquals(0, registry.count(98L));
        assertEquals(0, registry.count(94L));
    }

    @Test
    void openStream_hasStreamingHeadersAndNoConnectionClose() throws Exception {
        SseTestClient client = connect(PATH, tokens().supervisor(95004L, 95L, Duration.ofMinutes(5)));

        assertEquals(200, client.status());
        assertEquals("no-cache, no-transform", client.header("Cache-Control"));
        assertEquals("no", client.header("X-Accel-Buffering"));
        assertTrue(client.header("Content-Type").startsWith("text/event-stream"));
        assertTrue(client.headerValues("Connection").stream().noneMatch(v -> v.equalsIgnoreCase("close")),
            client.headerValues("Connection").toString());
        assertTrue(client.awaitComment("connected", Duration.ofSeconds(2)));
    }

    @Test
    void openStream_receivesKeepAliveComments() throws Exception {
        SseTestClient client = connect(PATH, tokens().supervisor(95005L, 95L, Duration.ofMinutes(5)));

        assertTrue(client.awaitComment("keep-alive", Duration.ofSeconds(3)));
    }

    @Test
    void streamClosesWhenTheTokenExpires() throws Exception {
        SseTestClient client = connect(PATH, tokens().supervisor(95006L, 99L, Duration.ofSeconds(2)));

        assertEquals(200, client.status());
        assertTrue(client.awaitEnd(Duration.ofSeconds(8)));
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (registry.count(99L) > 0 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, registry.count(99L));
    }

    @Test
    void publishedEvent_isDeliveredOverHttpToTheOwnCompanyOnly() throws Exception {
        SseTestClient own = connect(PATH, tokens().supervisor(95007L, 95L, Duration.ofMinutes(5)));
        SseTestClient foreign = connect(PATH, tokens().supervisor(95008L, 96L, Duration.ofMinutes(5)));
        assertTrue(own.awaitComment("connected", Duration.ofSeconds(2)));
        assertTrue(foreign.awaitComment("connected", Duration.ofSeconds(2)));

        facade.publish(() -> Optional.of(new SupervisorEvent(95L, NotificationContextFacade.EMERGENCY_ACTIVATED,
            "evt-http", Map.of("eventId", "evt-http"))));

        SseTestClient.Event event = own.next(Duration.ofSeconds(2));
        assertNotNull(event);
        assertEquals("EmergencyActivated", event.name());
        assertEquals("evt-http", event.id());
        assertNull(foreign.next(Duration.ofMillis(300)));
        assertFalse(foreign.allEvents().stream().anyMatch(e -> "evt-http".equals(e.id())));
    }
}
