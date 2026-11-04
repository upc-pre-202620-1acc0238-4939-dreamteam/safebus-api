package com.dreamteam.safebus.notification.interfaces;

import com.dreamteam.safebus.notification.support.DelayingProxy;
import com.dreamteam.safebus.notification.support.LiveStreamFixture;
import com.dreamteam.safebus.notification.support.SseTestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US20 S1: two buses, five subscriptions behind a 200 ms round trip, delivery within five seconds. */
class DeliveryTimeServerTest extends LiveStreamFixture {

    private static final Duration ONE_WAY = Duration.ofMillis(100);
    private static final Duration LIMIT = Duration.ofSeconds(5);
    private static final int SUBSCRIPTIONS = 5;
    private static final List<Long> MILLIS = Collections.synchronizedList(new ArrayList<>());

    private final ObjectMapper mapper = new ObjectMapper();

    @RepeatedTest(20)
    void driverEmergency_reachesFiveSubscribersWithinFiveSeconds(RepetitionInfo info) throws Exception {
        try (DelayingProxy proxy = new DelayingProxy(port, ONE_WAY)) {
            List<SseTestClient> own = new ArrayList<>();
            for (int i = 0; i < SUBSCRIPTIONS; i++) {
                SseTestClient client = stream(proxy.port(), supervisorToken(7000L + i, company.getId()));
                assertEquals(200, client.status());
                assertTrue(client.awaitComment("connected", Duration.ofSeconds(5)));
                own.add(client);
            }
            SseTestClient foreign = stream(proxy.port(), supervisorToken(7100L, company.getId() + 100_000L));
            assertTrue(foreign.awaitComment("connected", Duration.ofSeconds(5)));

            int bus = info.getCurrentRepetition() % 2;
            String emergencyId = UUID.randomUUID().toString();
            long start = System.nanoTime();
            assertEquals(201, postDriverEmergency(bus, emergencyId).statusCode());

            long slowest = 0;
            for (SseTestClient client : own) {
                SseTestClient.Event event = client.next(LIMIT);
                assertTrue(event != null, "no event within five seconds");
                assertEquals("EmergencyActivated", event.name());
                assertEquals(emergencyId, event.id());
                JsonNode payload = mapper.readTree(event.data());
                assertEquals("CRITICAL", payload.get("priority").asString());
                assertEquals(buses.get(bus).getId(), payload.get("bus").get("busId").asLong());
                long elapsed = Duration.ofNanos(event.receivedNanos() - start).toMillis();
                assertTrue(elapsed < LIMIT.toMillis(), "delivered after " + elapsed + " ms");
                slowest = Math.max(slowest, elapsed);
            }
            MILLIS.add(slowest);

            for (SseTestClient client : own) {
                assertNull(client.next(Duration.ofMillis(100)), "duplicate delivery");
            }
            assertNull(foreign.next(Duration.ofMillis(100)), "foreign company received data");
        }
    }

    @AfterAll
    static void report() throws Exception {
        List<Long> sorted = new ArrayList<>(MILLIS);
        Collections.sort(sorted);
        if (sorted.isEmpty()) {
            return;
        }
        long median = sorted.size() % 2 == 1 ? sorted.get(sorted.size() / 2)
            : (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2;
        String line = "runs=" + sorted.size() + " slowestSubscriberPerRun: max=" + sorted.getLast()
            + "ms median=" + median + "ms min=" + sorted.getFirst()
            + "ms (emulated 200 ms RTT, local hardware, " + SUBSCRIPTIONS + " subscriptions, 2 buses)";
        System.out.println("DELIVERY-TIMES " + line);
        Files.writeString(Path.of("target", "delivery-times.txt"), line + System.lineSeparator());
    }
}
