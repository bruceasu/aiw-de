package me.asu.aidev.development.application;

import me.asu.aidev.work.domain.TaskId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class DevelopmentTaskIds {
    private DevelopmentTaskIds() {}

    static TaskId review(TaskId source) { return derived(source, "review"); }
    static TaskId verification(TaskId source) { return derived(source, "verify"); }
    static TaskId repair(TaskId source) { return derived(source, "repair"); }

    private static TaskId derived(TaskId source, String suffix) {
        String raw = source.value() + "~" + suffix;
        if (raw.length() <= 128) return new TaskId(raw);
        String hash = sha256(source.value()).substring(0, 16);
        String tail = "~" + suffix + "~" + hash;
        int prefixLength = 128 - tail.length();
        return new TaskId(source.value().substring(0, prefixLength) + tail);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
