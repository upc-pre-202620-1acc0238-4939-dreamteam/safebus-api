package com.dreamteam.safebus.notification.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSubscriptionTest {

    private final RecordingSink sink = new RecordingSink();
    private final NotificationSubscription subscription = new NotificationSubscription(1L, 10L, sink, 2);

    @Test
    void drain_sendsFramesInOrder() {
        subscription.offer(new Frame("A", "1", "x"));
        subscription.offer(Frame.comment("keep-alive"));

        assertTrue(subscription.tryStartDrain());
        assertTrue(subscription.drain());

        assertEquals(List.of("A:1", "#keep-alive"), sink.received);
    }

    @Test
    void offer_reportsFullInsteadOfBlocking() {
        subscription.offer(new Frame("A", "1", "x"));
        subscription.offer(new Frame("A", "2", "x"));

        assertEquals(NotificationSubscription.Offer.FULL, subscription.offer(new Frame("A", "3", "x")));
    }

    @Test
    void drain_closesTheSubscriptionWhenThePeerFails() {
        sink.failing = true;
        subscription.offer(new Frame("A", "1", "x"));

        assertTrue(subscription.tryStartDrain());
        assertFalse(subscription.drain());

        assertTrue(subscription.isClosed());
        assertTrue(sink.closed);
        assertEquals(NotificationSubscription.Offer.CLOSED, subscription.offer(new Frame("A", "2", "x")));
    }

    @Test
    void tryStartDrain_allowsOnlyOneDrainAtATime() {
        assertTrue(subscription.tryStartDrain());
        assertFalse(subscription.tryStartDrain());

        subscription.cancelDrain();

        assertTrue(subscription.tryStartDrain());
    }
}
