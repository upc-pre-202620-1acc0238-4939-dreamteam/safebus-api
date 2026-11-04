package com.dreamteam.safebus.notification.application;

import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.SupervisorEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDispatcherTest {

    private final SubscriptionRegistry registry = new SubscriptionRegistry(3, 10, 2);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(4), new ThreadPoolExecutor.AbortPolicy());
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(registry, executor);

    @AfterEach
    void stop() {
        executor.shutdownNow();
    }

    private static SupervisorEvent event(Long companyId, String id) {
        return new SupervisorEvent(companyId, "EmergencyActivated", id, "payload-" + id);
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5 s");
            }
            Thread.sleep(5);
        }
    }

    @Test
    void publish_deliversOnlyToSubscribersOfTheEventCompany() throws Exception {
        RecordingSink own = new RecordingSink();
        RecordingSink foreign = new RecordingSink();
        registry.register(1L, 10L, own);
        registry.register(2L, 20L, foreign);

        dispatcher.publish(() -> Optional.of(event(10L, "E1")));

        eventually(() -> own.received.size() == 1);
        assertEquals(List.of("EmergencyActivated:E1"), own.received);
        Thread.sleep(100);
        assertTrue(foreign.received.isEmpty());
    }

    @Test
    void publish_keepsTheOrderOfEventsPerSubscriber() throws Exception {
        RecordingSink sink = new RecordingSink();
        registry.register(1L, 10L, sink);

        dispatcher.publish(() -> Optional.of(event(10L, "E1")));
        eventually(() -> sink.received.size() == 1);
        dispatcher.publish(() -> Optional.of(event(10L, "E2")));
        eventually(() -> sink.received.size() == 2);

        assertEquals(List.of("EmergencyActivated:E1", "EmergencyActivated:E2"), sink.received);
    }

    @Test
    void failingSubscriber_isRemovedAndDoesNotAffectTheOthers() throws Exception {
        RecordingSink dead = new RecordingSink();
        dead.failing = true;
        RecordingSink healthy = new RecordingSink();
        registry.register(1L, 10L, dead);
        registry.register(2L, 10L, healthy);

        assertDoesNotThrow(() -> dispatcher.publish(() -> Optional.of(event(10L, "E1"))));

        eventually(() -> healthy.received.size() == 1);
        eventually(() -> registry.count(10L) == 1);
        assertTrue(dead.closed);
    }

    @Test
    void blockedSubscriber_neitherDelaysTheCallerNorTheOtherSubscribers() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        EventSink blocked = new RecordingSink() {
            @Override
            public void send(String name, String id, Object payload) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        RecordingSink healthy = new RecordingSink();
        registry.register(1L, 10L, blocked);
        registry.register(2L, 10L, healthy);

        long start = System.nanoTime();
        dispatcher.publish(() -> Optional.of(event(10L, "E1")));
        long publishMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        eventually(() -> healthy.received.size() == 1);
        assertTrue(publishMillis < 200, "publish took " + publishMillis + " ms");
        release.countDown();
    }

    @Test
    void slowSubscriber_isClosedWhenItsMailboxOverflowsWhileOthersKeepReceiving() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        RecordingSink slow = new RecordingSink() {
            @Override
            public void send(String name, String id, Object payload) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        RecordingSink healthy = new RecordingSink();
        registry.register(1L, 10L, slow);
        registry.register(2L, 10L, healthy);

        for (int i = 1; i <= 6; i++) {
            String id = "E" + i;
            dispatcher.publish(() -> Optional.of(event(10L, id)));
            Thread.sleep(20);
        }

        eventually(() -> healthy.received.size() == 6);
        eventually(() -> registry.count(10L) == 1);
        assertTrue(slow.closed);
        release.countDown();
    }

    @Test
    void publish_neverThrowsWhenTheExecutorIsSaturated() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) {
            executor.execute(() -> {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertDoesNotThrow(() -> dispatcher.publish(() -> Optional.of(event(10L, "E1"))));

        assertEquals(1, dispatcher.droppedCount());
        hold.countDown();
    }

    @Test
    void publish_dropsAnEventWhoseLoaderFailsWithoutThrowing() throws Exception {
        RecordingSink sink = new RecordingSink();
        registry.register(1L, 10L, sink);

        dispatcher.publish(() -> {
            throw new IllegalStateException("database unavailable");
        });

        eventually(() -> dispatcher.droppedCount() == 1);
        assertTrue(sink.received.isEmpty());
    }

    @Test
    void publish_sendsNothingWhenTheLoaderReturnsNoEvent() throws Exception {
        RecordingSink sink = new RecordingSink();
        registry.register(1L, 10L, sink);

        dispatcher.publish(Optional::empty);

        Thread.sleep(100);
        assertTrue(sink.received.isEmpty());
        assertEquals(0, dispatcher.droppedCount());
    }

    @Test
    void sendKeepAlive_writesACommentToEverySubscriber() throws Exception {
        RecordingSink a = new RecordingSink();
        RecordingSink b = new RecordingSink();
        registry.register(1L, 10L, a);
        registry.register(2L, 20L, b);

        dispatcher.sendKeepAlive();

        eventually(() -> a.received.size() == 1 && b.received.size() == 1);
        assertEquals(List.of("#keep-alive"), a.received);
    }

    @Test
    void sendKeepAlive_removesADeadSubscriber() throws Exception {
        RecordingSink dead = new RecordingSink();
        dead.failing = true;
        registry.register(1L, 10L, dead);

        dispatcher.sendKeepAlive();

        eventually(() -> registry.count(10L) == 0);
        assertTrue(dead.closed);
    }
}
