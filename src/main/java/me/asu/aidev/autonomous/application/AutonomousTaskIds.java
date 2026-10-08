package me.asu.aidev.autonomous.application;

import me.asu.aidev.work.domain.TaskId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AutonomousTaskIds {
    private AutonomousTaskIds() {}

    static TaskId requirement(String runId) { return new TaskId(runId + "~requirement"); }
    static TaskId architecture(String runId) { return new TaskId(runId + "~architecture"); }
    static TaskId planning(String runId) { return new TaskId(runId + "~planning"); }
    /** R8-S1 compatibility id used when an old/plain plan summary has no work-item manifest. */
    static TaskId implementation(String runId) { return bounded(runId + "~implementation", runId, "implementation"); }
    static TaskId implementationItem(String runId, String key) {
        return bounded(runId + "~implementation~" + key, runId + "~" + key, "impl~" + key);
    }
    static TaskId integration(String runId) { return bounded(runId + "~integration", runId, "integration"); }

    private static TaskId bounded(String raw, String hashSource, String suffix) {
        if (raw.length() <= 128) return new TaskId(raw);
        String hash = sha256(hashSource).substring(0, 16);
        String tail = "~" + suffix + "~" + hash;
        int prefixLength = 128 - tail.length();
        return new TaskId(raw.substring(0, Math.max(1, prefixLength)) + tail);
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
