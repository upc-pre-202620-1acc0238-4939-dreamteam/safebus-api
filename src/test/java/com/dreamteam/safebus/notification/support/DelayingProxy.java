package com.dreamteam.safebus.notification.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/** Local TCP relay that holds every chunk for a fixed delay in each direction, to emulate a slow network. */
public final class DelayingProxy implements AutoCloseable {

    private record Chunk(byte[] data, int length, long dueNanos) {}

    private final ServerSocket server;
    private final int targetPort;
    private final long delayNanos;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    public DelayingProxy(int targetPort, Duration oneWayDelay) throws IOException {
        this.targetPort = targetPort;
        this.delayNanos = oneWayDelay.toNanos();
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "delaying-proxy-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                Socket upstream = new Socket(InetAddress.getLoopbackAddress(), targetPort);
                sockets.add(client);
                sockets.add(upstream);
                pump(client, upstream, "up");
                pump(upstream, client, "down");
            } catch (IOException ex) {
                return;
            }
        }
    }

    private void pump(Socket from, Socket to, String name) {
        LinkedBlockingQueue<Chunk> queue = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try (InputStream in = from.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    byte[] copy = new byte[read];
                    System.arraycopy(buffer, 0, copy, 0, read);
                    queue.add(new Chunk(copy, read, System.nanoTime() + delayNanos));
                }
            } catch (IOException ignored) {
                // the peer closed
            } finally {
                queue.add(new Chunk(null, -1, System.nanoTime() + delayNanos));
            }
        }, "delaying-proxy-reader-" + name);
        Thread writer = new Thread(() -> {
            try (OutputStream out = to.getOutputStream()) {
                while (true) {
                    Chunk chunk = queue.take();
                    long wait = chunk.dueNanos() - System.nanoTime();
                    if (wait > 0) {
                        Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                    }
                    if (chunk.data() == null) {
                        return;
                    }
                    out.write(chunk.data(), 0, chunk.length());
                    out.flush();
                }
            } catch (IOException | InterruptedException ignored) {
                // the peer closed
            } finally {
                closeQuietly(from);
                closeQuietly(to);
            }
        }, "delaying-proxy-writer-" + name);
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
            // already closed
        }
        sockets.forEach(DelayingProxy::closeQuietly);
    }
}
