package com.dreamteam.safebus.notification.application;

import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.SupervisorEvent;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Delivery is best effort and at most once: nothing here may fail, delay or roll back the caller.
 * An event that cannot be queued or loaded is counted and dropped; clients recover it through REST.
 */
public class NotificationDispatcher {

    private static final String KEEP_ALIVE = "keep-alive";

    private final SubscriptionRegistry registry;
    private final ExecutorService executor;
    private final AtomicLong dropped = new AtomicLong();

    public NotificationDispatcher(SubscriptionRegistry registry, ExecutorService executor) {
        this.registry = registry;
        this.executor = executor;
    }

    public void publish(Supplier<Optional<SupervisorEvent>> loader) {
        try {
            executor.execute(() -> fanOut(loader));
        } catch (RuntimeException ex) {
            dropped.incrementAndGet();
        }
    }

    public void sendKeepAlive() {
        try {
            for (NotificationSubscription subscription : registry.all()) {
                deliver(subscription, Frame.comment(KEEP_ALIVE), false);
            }
        } catch (RuntimeException ex) {
            dropped.incrementAndGet();
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public void shutdown() {
        registry.all().forEach(registry::remove);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void fanOut(Supplier<Optional<SupervisorEvent>> loader) {
        try {
            Optional<SupervisorEvent> event = loader.get();
            if (event.isEmpty()) {
                return;
            }
            Frame frame = new Frame(event.get().name(), event.get().id(), event.get().payload());
            for (NotificationSubscription subscription : registry.ofCompany(event.get().companyId())) {
                deliver(subscription, frame, true);
            }
        } catch (RuntimeException ex) {
            dropped.incrementAndGet();
        }
    }

    private void deliver(NotificationSubscription subscription, Frame frame, boolean closeWhenFull) {
        switch (subscription.offer(frame)) {
            case QUEUED -> schedule(subscription);
            case FULL -> {
                if (closeWhenFull) {
                    registry.remove(subscription);
                }
            }
            case CLOSED -> registry.remove(subscription);
        }
    }

    private void schedule(NotificationSubscription subscription) {
        if (!subscription.tryStartDrain()) {
            return;
        }
        try {
            executor.execute(() -> drain(subscription));
        } catch (RuntimeException ex) {
            subscription.cancelDrain();
            dropped.incrementAndGet();
        }
    }

    private void drain(NotificationSubscription subscription) {
        try {
            boolean alive = subscription.drain();
            if (!alive) {
                registry.remove(subscription);
            } else if (subscription.hasPending()) {
                schedule(subscription);
            }
        } catch (RuntimeException ex) {
            registry.remove(subscription);
        }
    }
}
