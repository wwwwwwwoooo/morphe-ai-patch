/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Private app cache, never shared with lyrics providers. A generation prevents writes after clear. */
public final class TranslationDiskCache {
    private final File directory;
    private long generation;
    public TranslationDiskCache(File directory) { this.directory = directory; }
    public synchronized long generation() { return generation; }
    public synchronized List<String> read(String key, List<String> lines) {
        try {
            Path path = new File(directory, key + ".json").toPath();
            if (Files.size(path) > 2_000_000) return null;
            return TranslationFormat.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), lines);
        } catch (Exception ignored) { return null; }
    }
    public synchronized void write(String key, String json, List<String> lines, long started) throws Exception {
        TranslationFormat.parse(json, lines);
        if (started != generation) return;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cache directory unavailable");
        Path tmp = Files.createTempFile(directory.toPath(), "translation-", ".tmp");
        try {
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, new File(directory, key + ".json").toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(tmp); }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null && files.length > 200) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - 200; i++) Files.deleteIfExists(files[i].toPath());
        }
    }
    public synchronized void clear() throws IOException {
        generation++;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
    }
}
