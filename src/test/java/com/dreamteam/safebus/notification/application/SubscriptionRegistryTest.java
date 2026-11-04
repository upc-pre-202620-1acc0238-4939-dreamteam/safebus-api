package com.dreamteam.safebus.notification.application;

import com.dreamteam.safebus.shared.domain.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRegistryTest {

    private final SubscriptionRegistry registry = new SubscriptionRegistry(2, 3, 4);

    @Test
    void register_rejectsTheThirdStreamOfOneUser() {
        registry.register(1L, 10L, new RecordingSink());
        registry.register(1L, 10L, new RecordingSink());

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class,
            () -> registry.register(1L, 10L, new RecordingSink()));

        assertEquals("SUBSCRIPTION_LIMIT_USER", ex.code());
        assertEquals(2, registry.count(10L));
    }

    @Test
    void register_rejectsTheStreamBeyondTheCompanyLimit() {
        registry.register(1L, 10L, new RecordingSink());
        registry.register(2L, 10L, new RecordingSink());
        registry.register(3L, 10L, new RecordingSink());

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class,
            () -> registry.register(4L, 10L, new RecordingSink()));

        assertEquals("SUBSCRIPTION_LIMIT_COMPANY", ex.code());
    }

    @Test
    void limitsAreCountedPerCompany() {
        registry.register(1L, 10L, new RecordingSink());
        registry.register(1L, 10L, new RecordingSink());

        registry.register(1L, 20L, new RecordingSink());

        assertEquals(2, registry.count(10L));
        assertEquals(1, registry.count(20L));
    }

    @Test
    void remove_freesTheSlotAndClosesTheSink() {
        RecordingSink sink = new RecordingSink();
        NotificationSubscription first = registry.register(1L, 10L, sink);
        registry.register(1L, 10L, new RecordingSink());

        registry.remove(first);
        registry.register(1L, 10L, new RecordingSink());

        assertEquals(2, registry.count(10L));
        assertTrue(sink.closed);
        assertTrue(first.isClosed());
    }

    @Test
    void register_ignoresSubscriptionsClosedBeforeBeingRemoved() {
        NotificationSubscription dead = registry.register(1L, 10L, new RecordingSink());
        registry.register(1L, 10L, new RecordingSink());
        dead.close();

        registry.register(1L, 10L, new RecordingSink());

        assertEquals(2, registry.count(10L));
    }

    @Test
    void ofCompany_returnsOnlyThatCompany() {
        NotificationSubscription own = registry.register(1L, 10L, new RecordingSink());
        registry.register(2L, 20L, new RecordingSink());

        assertEquals(java.util.List.of(own), registry.ofCompany(10L));
        assertTrue(registry.ofCompany(99L).isEmpty());
    }
}
