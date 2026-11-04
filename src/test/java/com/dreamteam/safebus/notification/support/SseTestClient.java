package com.dreamteam.safebus.notification.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Minimal real-HTTP SSE reader: one thread parses frames and timestamps each one on arrival. */
public final class SseTestClient implements AutoCloseable {

    public record Event(String name, String id, String data, long receivedNanos) {}

    private final HttpClient client = HttpClient.newHttpClient();
    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final List<String> comments = new CopyOnWriteArrayList<>();
    private final CountDownLatch ended = new CountDownLatch(1);
    private final List<Event> allEvents = new CopyOnWriteArrayList<>();
    private HttpResponse<InputStream> response;
    private String errorBody;
    private Thread reader;

    private SseTestClient() {}

    public static SseTestClient connect(String host, int port, String path, String bearer) throws Exception {
        SseTestClient sse = new SseTestClient();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + path))
            .header("Accept", "text/event-stream");
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        sse.response = sse.client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        sse.startReading();
        return sse;
    }

    public int status() {
        return response.statusCode();
    }

    public String header(String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    public List<String> headerValues(String name) {
        return response.headers().allValues(name);
    }

    public String errorBody() {
        return errorBody;
    }

    public List<String> comments() {
        return comments;
    }

    public List<Event> allEvents() {
        return allEvents;
    }

    public Event next(Duration timeout) throws InterruptedException {
        return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean awaitEnd(Duration timeout) throws InterruptedException {
        return ended.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean awaitComment(String text, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (comments.contains(text)) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private void startReading() {
        reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                if (response.statusCode() != 200) {
                    errorBody = String.join("\n", in.lines().toList());
                    return;
                }
                String name = null;
                String id = null;
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    long arrived = System.nanoTime();
                    if (line.isEmpty()) {
                        if (name != null || data.length() > 0) {
                            Event event = new Event(name, id, data.toString(), arrived);
                            allEvents.add(event);
                            events.add(event);
                        }
                        name = null;
                        id = null;
                        data.setLength(0);
                    } else if (line.startsWith(":")) {
                        comments.add(line.substring(1).trim());
                    } else if (line.startsWith("event:")) {
                        name = line.substring(6).trim();
                    } else if (line.startsWith("id:")) {
                        id = line.substring(3).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    }
                }
            } catch (IOException ignored) {
                // the connection was closed
            } finally {
                ended.countDown();
            }
        }, "sse-test-reader");
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public void close() {
        client.shutdownNow();
    }
}
