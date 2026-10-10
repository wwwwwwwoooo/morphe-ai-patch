/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import java.io.Closeable;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;

public final class RequestCancellation {
    private final long deadline = System.nanoTime() + 180_000_000_000L;
    private volatile boolean cancelled;
    private HttpURLConnection connection;
    private Closeable listener;

    public void check() throws InterruptedIOException {
        if (System.nanoTime() > deadline) throw new java.net.SocketTimeoutException("Request timed out");
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
    }
    public boolean isCancelled() { return cancelled; }
    public synchronized void attach(HttpURLConnection next) throws InterruptedIOException {
        check(); connection = next;
    }
    public synchronized void attach(Closeable next) throws InterruptedIOException {
        check(); listener = next;
    }
    public synchronized void detach() { connection = null; }
    public synchronized void cancel() {
        cancelled = true;
        if (connection != null) connection.disconnect();
        if (listener != null) try { listener.close(); } catch (Exception ignored) { }
    }
    public void backoff(int attempt) throws InterruptedIOException {
        for (int i = 0; i < (5 << attempt); i++) {
            check();
            try { Thread.sleep(100); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); check();
            }
        }
    }
}
