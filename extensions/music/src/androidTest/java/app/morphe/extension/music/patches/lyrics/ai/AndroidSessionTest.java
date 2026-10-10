/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.UUID;
import java.util.Map;
import java.net.Socket;
import java.net.URI;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Run on the extension test application, never against a user's YouTube Music data. */
@RunWith(AndroidJUnit4.class)
public class AndroidSessionTest {
    private File directory;
    private SecureSessionStore store;

    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = new File(target.getNoBackupFilesDir(), "lyrics-auth-test-" + UUID.randomUUID());
        assertTrue(directory.mkdir());
        store = new SecureSessionStore(new ContextWrapper(target) {
            @Override public File getNoBackupFilesDir() { return directory; }
        });
    }
    @After public void tearDown() throws Exception {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(directory.toPath());
    }
    @Test public void keystoreRoundTripAndRotationNeverWritePlaintext() throws Exception {
        JSONObject first = new JSONObject().put("access_token", "fixture-access-1").put("refresh_token", "fixture-refresh-1");
        store.write(first);
        assertEquals("fixture-access-1", store.read().getString("access_token"));
        byte[] bytes = Files.readAllBytes(new File(directory, "lyricbridge-chatgpt-session").toPath());
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("fixture-access-1"));
        store.write(new JSONObject().put("access_token", "fixture-access-2").put("refresh_token", "fixture-refresh-2"));
        assertEquals("fixture-refresh-2", store.read().getString("refresh_token"));
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        assertNull(keys.getKey("lyricbridge.chatgpt.session.v1", null).getEncoded());
    }
    @Test public void tamperedEncryptedStoreFailsClosed() throws Exception {
        store.write(new JSONObject().put("access_token", "fixture-access"));
        File file = new File(directory, "lyricbridge-chatgpt-session");
        byte[] bytes = Files.readAllBytes(file.toPath()); bytes[bytes.length - 1] ^= 1;
        Files.write(file.toPath(), bytes);
        assertThrows(Exception.class, () -> store.read());
    }
    @Test public void androidLoopbackReceivesTheOfficialCallback() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (var receiver = new LoopbackReceiver()) {
            var pending = executor.submit(() -> receiver.await("test-state", new RequestCancellation()));
            URI uri = URI.create(receiver.redirect());
            assertEquals("127.0.0.1", uri.getHost());
            assertEquals("/auth/callback", uri.getPath());
            try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
                socket.setSoTimeout(5000);
                String request = "GET /auth/callback?state=test-state&code=test-code&client_id=issued HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n\r\n";
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                String status = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
                assertTrue(status.contains("200 OK"));
            }
            Map<String, String> callback = pending.get(5, TimeUnit.SECONDS);
            assertEquals("test-code", callback.get("code"));
            assertEquals("test-state", callback.get("state"));
        } finally { executor.shutdownNow(); }
    }

}
