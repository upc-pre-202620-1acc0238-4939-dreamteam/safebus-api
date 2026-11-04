package com.dreamteam.safebus.notification.application;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NotificationSubscription {

    public enum Offer { QUEUED, FULL, CLOSED }

    private final Long userId;
    private final Long companyId;
    private final EventSink sink;
    private final BlockingQueue<Frame> mailbox;
    private final AtomicBoolean draining = new AtomicBoolean();
    private volatile boolean closed;

    public NotificationSubscription(Long userId, Long companyId, EventSink sink, int mailboxCapacity) {
        this.userId = userId;
        this.companyId = companyId;
        this.sink = sink;
        this.mailbox = new ArrayBlockingQueue<>(mailboxCapacity);
    }

    public Long userId() {
        return userId;
    }

    public Long companyId() {
        return companyId;
    }

    public boolean isClosed() {
        return closed;
    }

    public Offer offer(Frame frame) {
        if (closed) {
            return Offer.CLOSED;
        }
        return mailbox.offer(frame) ? Offer.QUEUED : Offer.FULL;
    }

    /** At most one drain runs per subscription, so one slow peer never occupies more than one thread. */
    public boolean tryStartDrain() {
        return !closed && draining.compareAndSet(false, true);
    }

    public void cancelDrain() {
        draining.set(false);
    }

    /** Sends queued frames in order; returns false when the peer failed and the subscription is closed. */
    public boolean drain() {
        try {
            Frame frame;
            while (!closed && (frame = mailbox.poll()) != null) {
                try {
                    if (frame.isComment()) {
                        sink.comment((String) frame.payload());
                    } else {
                        sink.send(frame.name(), frame.id(), frame.payload());
                    }
                } catch (SinkClosedException | RuntimeException ex) {
                    close();
                    return false;
                }
            }
            return !closed;
        } finally {
            draining.set(false);
        }
    }

    public boolean hasPending() {
        return !closed && !mailbox.isEmpty();
    }

    public void close() {
        closed = true;
        mailbox.clear();
        try {
            sink.close();
        } catch (RuntimeException ignored) {
            // the peer is already gone
        }
    }
}
