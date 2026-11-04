package com.dreamteam.safebus.notification.application;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class RecordingSink implements EventSink {

    final List<String> received = new CopyOnWriteArrayList<>();
    volatile boolean failing;
    volatile boolean closed;

    @Override
    public void send(String name, String id, Object payload) throws SinkClosedException {
        if (failing) {
            throw new SinkClosedException(new java.io.IOException("peer gone"));
        }
        received.add(name + ":" + id);
    }

    @Override
    public void comment(String text) throws SinkClosedException {
        if (failing) {
            throw new SinkClosedException(new java.io.IOException("peer gone"));
        }
        received.add("#" + text);
    }

    @Override
    public void close() {
        closed = true;
    }
}
