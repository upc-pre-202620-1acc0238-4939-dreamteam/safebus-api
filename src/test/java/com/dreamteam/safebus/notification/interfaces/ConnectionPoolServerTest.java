package com.dreamteam.safebus.notification.interfaces;

import com.dreamteam.safebus.notification.support.LiveStreamFixture;
import com.dreamteam.safebus.notification.support.SseTestClient;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Open streams must not pin database connections: the production pool has only five. */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=5")
class ConnectionPoolServerTest extends LiveStreamFixture {

    private static final int STREAMS = 12;
    private static final Duration REST_LIMIT = Duration.ofSeconds(2);

    @Autowired DataSource dataSource;

    private HikariDataSource pool() throws Exception {
        return dataSource.unwrap(HikariDataSource.class);
    }

    @Test
    void twelveOpenStreams_holdNoConnectionAndRestAndWritesStillWork() throws Exception {
        assertEquals(5, pool().getMaximumPoolSize());

        List<SseTestClient> own = new ArrayList<>();
        for (int i = 0; i < STREAMS; i++) {
            long companyId = i < 6 ? company.getId() : company.getId() + 1000L + i;
            SseTestClient client = stream(port, supervisorToken(7200L + i, companyId));
            assertEquals(200, client.status());
            assertTrue(client.awaitComment("connected", Duration.ofSeconds(5)));
            if (i < 6) {
                own.add(client);
            }
        }
        assertEquals(6, registry.count(company.getId()));
        assertEquals(0, pool().getHikariPoolMXBean().getActiveConnections());

        String token = supervisorToken(7300L, company.getId());
        long slowest = 0;
        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            HttpResponse<String> response = reviewQueue(token);
            long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertEquals(200, response.statusCode());
            assertTrue(elapsed < REST_LIMIT.toMillis(), "review queue took " + elapsed + " ms");
            slowest = Math.max(slowest, elapsed);
        }
        System.out.println("POOL-CHECK streams=" + STREAMS + " poolSize=5 reviewQueueSlowest=" + slowest + "ms");

        String emergencyId = UUID.randomUUID().toString();
        assertEquals(201, postDriverEmergency(0, emergencyId).statusCode());
        for (SseTestClient client : own) {
            SseTestClient.Event event = client.next(Duration.ofSeconds(5));
            assertNotNull(event);
            assertEquals(emergencyId, event.id());
        }
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from emergency where id = ?", Integer.class, emergencyId));

        assertEquals(0, pool().getHikariPoolMXBean().getActiveConnections());
    }
}
