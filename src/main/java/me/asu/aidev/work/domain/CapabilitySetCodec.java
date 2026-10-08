package me.asu.aidev.work.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Compact portable persistence encoding for capability sets.
 * Capability names deliberately exclude comma so the same representation works in SQLite and PostgreSQL.
 */
public final class CapabilitySetCodec {
    private CapabilitySetCodec() {}

    public static String encode(Set<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) return "";
        return capabilities.stream()
                .map(CapabilitySetCodec::normalize)
                .sorted()
                .distinct()
                .collect(Collectors.joining(","));
    }

    public static Set<String> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return Set.of();
        TreeSet<String> out = Arrays.stream(encoded.split(","))
                .map(CapabilitySetCodec::normalize)
                .collect(Collectors.toCollection(TreeSet::new));
        return Set.copyOf(out);
    }

    private static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("capability is null");
        String normalized = value.trim().toLowerCase();
        if (normalized.isEmpty()) throw new IllegalArgumentException("capability is blank");
        if (normalized.length() > 128) throw new IllegalArgumentException("capability is too long");
        if (normalized.indexOf(',') >= 0) throw new IllegalArgumentException("capability cannot contain comma");
        return normalized;
    }
}
