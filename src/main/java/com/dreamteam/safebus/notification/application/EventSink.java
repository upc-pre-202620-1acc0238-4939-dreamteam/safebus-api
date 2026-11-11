package com.dreamteam.safebus.notification.application;

public interface EventSink {

    /** Writes one named event; a failure means the peer is gone. */
    void send(String name, String id, Object payload) throws SinkClosedException;

    /** Writes a comment frame that clients ignore. */
    void comment(String text) throws SinkClosedException;

    void close();
}
