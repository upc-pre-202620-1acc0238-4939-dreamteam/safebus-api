package com.dreamteam.safebus.notification.support;

import com.dreamteam.safebus.notification.application.EventSink;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process subscriber that keeps every event it is given, payload included. */
public final class CapturingSink implements EventSink {

    public record Captured(String name, String id, Object payload, long nanos) {}

    private final List<Captured> events = new CopyOnWriteArrayList<>();

    @Override
    public void send(String name, String id, Object payload) {
        events.add(new Captured(name, id, payload, System.nanoTime()));
    }

    @Override
    public void comment(String text) {
    }

    @Override
    public void close() {
    }

    public List<Captured> events() {
        return events;
    }

    public List<Captured> named(String name) {
        return events.stream().filter(e -> e.name().equals(name)).toList();
    }

    public boolean awaitCount(int count, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (events.size() >= count) {
                return true;
            }
            Thread.sleep(5);
        }
        return events.size() >= count;
    }

    /** Waits for the quiet period to pass and reports how many events arrived in total. */
    public int settledCount(Duration quiet) throws InterruptedException {
        Thread.sleep(quiet.toMillis());
        return events.size();
    }
}
