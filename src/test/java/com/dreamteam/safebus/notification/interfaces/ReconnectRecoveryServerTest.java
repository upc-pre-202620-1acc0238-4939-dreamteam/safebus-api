package com.dreamteam.safebus.notification.interfaces;

import com.dreamteam.safebus.notification.support.LiveStreamFixture;
import com.dreamteam.safebus.notification.support.SseTestClient;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestPropertySource(properties = {
    "safebus.scheduling.enabled=true",
    "safebus.notifications.keep-alive=PT0.3S"
})
class ReconnectRecoveryServerTest extends LiveStreamFixture {

    private static final long SUPERVISOR = 7400L;
    private final ObjectMapper mapper = new ObjectMapper();

    private void awaitNoSubscriptions() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (registry.count(company.getId()) > 0 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, registry.count(company.getId()));
    }

    @Test
    void s3_reconnectRecoversByRestAndResumesLiveWithoutDuplicates() throws Exception {
        SseTestClient first = stream(port, supervisorToken(SUPERVISOR, company.getId()));
        assertTrue(first.awaitComment("connected", Duration.ofSeconds(5)));
        closeStream(first);
        awaitNoSubscriptions();

        String missedEmergency = UUID.randomUUID().toString();
        assertEquals(201, postDriverEmergency(0, missedEmergency).statusCode());
        PassengerRequestGroup approved = awaitingGroup(0);
        PassengerRequestGroup pending = awaitingGroup(1);
        String bearer = supervisorToken(SUPERVISOR, company.getId());
        assertEquals(201, approve(approved.getId(), bearer).statusCode());

        SseTestClient second = stream(port, supervisorToken(SUPERVISOR, company.getId()));
        assertEquals(200, second.status());
        assertTrue(second.awaitComment("connected", Duration.ofSeconds(5)));
        assertNull(second.next(Duration.ofMillis(500)), "the stream must not replay missed cases");

        JsonNode queue = mapper.readTree(reviewQueue(bearer).body());
        Set<String> driverIds = new HashSet<>();
        queue.get("driverEmergencies").forEach(e -> driverIds.add(e.get("id").asString()));
        Set<Long> passengerGroups = new HashSet<>();
        queue.get("passengerEmergencies").forEach(e -> passengerGroups.add(e.get("groupId").asLong()));
        assertTrue(driverIds.contains(missedEmergency));
        assertEquals(1, passengerGroups.size());
        assertTrue(passengerGroups.contains(approved.getId()));
        JsonNode awaiting = queue.get("groupsAwaitingApproval");
        assertEquals(1, awaiting.size());
        assertEquals(pending.getId(), awaiting.get(0).get("groupId").asLong());
        String pendingReference = awaiting.get(0).get("reference").asString();

        String liveEmergency = UUID.randomUUID().toString();
        assertEquals(201, postDriverEmergency(1, liveEmergency).statusCode());
        SseTestClient.Event live = second.next(Duration.ofSeconds(5));
        assertNotNull(live);
        assertEquals(liveEmergency, live.id());
        assertTrue(!driverIds.contains(live.id()));

        assertEquals(200, approve(approved.getId(), bearer).statusCode());
        assertEquals(201, approve(pending.getId(), bearer).statusCode());
        SseTestClient.Event approval = second.next(Duration.ofSeconds(5));
        assertNotNull(approval);
        assertEquals("EmergencyActivated", approval.name());
        assertEquals(pendingReference, mapper.readTree(approval.data()).get("groupReference").asString());
        assertNull(second.next(Duration.ofMillis(500)), "a repeated approval must not notify again");
    }

    @Test
    void s4_foreignCompanyIsRejectedAndForeignStreamReceivesNothing() throws Exception {
        SseTestClient named = SseTestClient.connect("localhost", port,
            "/api/v1/notifications/stream?companyId=" + (company.getId() + 1), supervisorToken(7411L, company.getId()));
        assertEquals(403, named.status());
        assertTrue(named.awaitEnd(Duration.ofSeconds(5)));
        assertTrue(named.allEvents().isEmpty());
        assertTrue(named.errorBody() == null || !named.errorBody().contains("EmergencyActivated"));
        named.close();
        assertEquals(0, registry.count(company.getId() + 1));

        SseTestClient foreign = stream(port, supervisorToken(7412L, company.getId() + 1));
        assertTrue(foreign.awaitComment("connected", Duration.ofSeconds(5)));
        assertEquals(201, postDriverEmergency(0, UUID.randomUUID().toString()).statusCode());
        assertNull(foreign.next(Duration.ofMillis(800)), "foreign subscriber received data");
    }
}
