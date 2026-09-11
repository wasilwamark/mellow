package com.acaciawave.mellow.distro;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for {@code /etc/os-release} (port of {@code distro.DetectOSRelease}).
 */
public final class OsRelease {

    private OsRelease() {
    }

    /** Parses key=value pairs, tolerating quotes, comments and blank lines. */
    public static Map<String, String> parse(String content) {
        Map<String, String> values = new HashMap<>();
        if (content == null) {
            return values;
        }
        for (String rawLine : content.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            int eq = line.indexOf('=');
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2
                    && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    /** Maps parsed os-release values to a {@link DistroInfo}. */
    public static DistroInfo toDistroInfo(Map<String, String> osRelease) {
        String id = osRelease.getOrDefault("ID", "unknown");
        String name = osRelease.getOrDefault("NAME", id);
        String version = osRelease.getOrDefault("VERSION_ID", "");
        DistroFamily family = DistroFamily.fromId(id);
        return new DistroInfo(id, name, version, family, PackageManagerType.forFamily(family));
    }

    public static DistroInfo detect(String osReleaseContent) {
        return toDistroInfo(parse(osReleaseContent));
    }
}