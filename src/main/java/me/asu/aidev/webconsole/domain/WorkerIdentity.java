package me.asu.aidev.webconsole.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Pure visual identity. Does not infer a worker's role, availability or health. */
public record WorkerIdentity(int silhouette, int hue, String mark) {
    public static WorkerIdentity forId(String id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            int value = Byte.toUnsignedInt(digest[0]);
            return new WorkerIdentity(value % 4, 170 + Byte.toUnsignedInt(digest[1]) % 61,
                    String.format("%02X%02X", value, Byte.toUnsignedInt(digest[2])));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", error);
        }
    }
}
