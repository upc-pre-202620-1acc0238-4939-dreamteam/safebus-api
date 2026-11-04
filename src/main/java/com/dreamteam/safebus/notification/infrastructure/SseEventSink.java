package com.dreamteam.safebus.notification.infrastructure;

import com.dreamteam.safebus.notification.application.EventSink;
import com.dreamteam.safebus.notification.application.SinkClosedException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public class SseEventSink implements EventSink {

    private final SseEmitter emitter;

    public SseEventSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String name, String id, Object payload) throws SinkClosedException {
        write(SseEmitter.event().name(name).id(id).data(payload, MediaType.APPLICATION_JSON));
    }

    @Override
    public void comment(String text) throws SinkClosedException {
        write(SseEmitter.event().comment(text));
    }

    @Override
    public void close() {
        emitter.complete();
    }

    private void write(SseEmitter.SseEventBuilder event) throws SinkClosedException {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            throw new SinkClosedException(ex);
        }
    }
}
