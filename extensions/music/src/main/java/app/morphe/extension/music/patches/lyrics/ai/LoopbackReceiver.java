/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Browser and app must run on the same device. Never binds a wildcard address. */
public final class LoopbackReceiver implements Closeable {
    private final ServerSocket server;
    public LoopbackReceiver() throws IOException {
        server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1);
    }
    public String redirect() { return "http://127.0.0.1:" + server.getLocalPort() + "/auth/callback"; }
    public Map<String, String> await(String state, RequestCancellation cancel) throws Exception {
        long deadline = System.nanoTime() + 180_000_000_000L;
        cancel.attach(this);
        while (System.nanoTime() < deadline) {
            cancel.check(); server.setSoTimeout(1000);
            Socket socket;
            try { socket = server.accept(); } catch (SocketTimeoutException ignored) { continue; }
            try (socket) {
                socket.setSoTimeout(2000);
                InputStream input = socket.getInputStream();
                String first = readLine(input);
                int headerBytes = 0;
                for (;;) {
                    String header = readLine(input);
                    if (header.isEmpty()) break;
                    headerBytes += header.length();
                    if (headerBytes > 16_384) throw new IOException("Callback headers too large");
                }
                String[] request = first.split(" ");
                Map<String, String> query = null;
                try {
                    if (request.length == 3 && "GET".equals(request[0])) {
                        URI uri = new URI(request[1]);
                        if (!uri.isAbsolute() && "/auth/callback".equals(uri.getRawPath())) query = query(uri.getRawQuery());
                    }
                } catch (Exception ignored) { }
                boolean valid = query != null && OAuthProtocol.equal(state, query.get("state"));
                String body = valid ? "Callback received. Return to YouTube Music to check sign-in status."
                        : "Invalid callback.";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                OutputStream out = socket.getOutputStream();
                out.write(((valid ? "HTTP/1.1 200 OK" : "HTTP/1.1 400 Bad Request")
                        + "\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store"
                        + "\r\nContent-Security-Policy: default-src 'none'\r\nConnection: close\r\nContent-Length: "
                        + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(bytes); out.flush();
                if (valid) return query;
            } catch (SocketTimeoutException ignored) { }
        }
        throw new SocketTimeoutException("Sign-in timed out");
    }
    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(); int ch;
        while ((ch = input.read()) != -1 && ch != '\n') {
            if (line.size() >= 16_384) throw new IOException("Callback too large");
            if (ch != '\r') line.write(ch);
        }
        return line.toString("US-ASCII");
    }
    public static Map<String, String> query(String raw) throws Exception {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.length() > 16_384) throw OAuthProtocol.protocol("invalid_callback");
        for (String pair : raw.split("&")) {
            String[] bits = pair.split("=", 2);
            String key = URLDecoder.decode(bits[0], "UTF-8");
            String value = bits.length == 2 ? URLDecoder.decode(bits[1], "UTF-8") : "";
            if (out.put(key, value) != null) throw OAuthProtocol.protocol("duplicate_callback_parameter");
        }
        return out;
    }
    @Override public void close() throws IOException { server.close(); }
}
