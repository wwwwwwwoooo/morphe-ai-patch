/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials are encrypted with a non-exportable Keystore key and excluded from app backups. */
public final class SecureSessionStore {
    private static final String ALIAS = "lyricbridge.chatgpt.session.v1";
    private static final byte[] AAD = "lyricbridge.oauth.v1".getBytes(StandardCharsets.US_ASCII);
    private final AtomicFile file;
    public SecureSessionStore(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "lyricbridge-chatgpt-session"));
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    public synchronized JSONObject read() throws Exception {
        if (!file.getBaseFile().exists()) return new JSONObject();
        byte[] bytes = file.readFully();
        if (bytes.length < 29 || bytes.length > 1_000_000 || bytes[0] != 1) throw new IOException("Invalid session store");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 1, 12)); cipher.updateAAD(AAD);
        return new JSONObject(new String(cipher.doFinal(bytes, 13, bytes.length - 13), StandardCharsets.UTF_8));
    }
    public synchronized void write(JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        FileOutputStream out = file.startWrite();
        try { out.write(1); out.write(cipher.getIV()); out.write(encrypted); file.finishWrite(out); }
        catch (Exception ex) { file.failWrite(out); throw ex; }
    }
}
