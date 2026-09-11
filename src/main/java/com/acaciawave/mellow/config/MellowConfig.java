package com.acaciawave.mellow.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.acaciawave.mellow.MellowException;

/**
 * Mellow's on-disk configuration: {@code ~/.mellow/aliases.json} and
 * {@code ~/.mellow/secrets.json} (0600). File formats are kept byte-compatible
 * with the Go implementation.
 */
public final class MellowConfig {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final Path configDir;
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, String> aliases = new LinkedHashMap<>();
    private Map<String, String> secrets = new LinkedHashMap<>();

    public MellowConfig() {
        this(defaultDir());
    }

    public MellowConfig(Path configDir) {
        this.configDir = configDir;
        this.aliases = load(configDir.resolve("aliases.json"));
        this.secrets = load(configDir.resolve("secrets.json"));
    }

    private static Path defaultDir() {
        return Path.of(System.getProperty("user.home"), ".mellow");
    }

    public Path configDir() {
        return configDir;
    }

    private Map<String, String> load(Path file) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> values = mapper.readValue(Files.readString(file), STRING_MAP);
            return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private void save(Path file, Map<String, String> values, boolean secret) {
        try {
            Files.createDirectories(configDir);
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(values));
            if (secret) {
                restrictPermissions(file);
            }
        } catch (IOException e) {
            throw new MellowException("failed to save " + file + ": " + e.getMessage(), e);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem — best effort, matching Go's 0600 intent.
        }
    }

    // --- aliases -----------------------------------------------------------

    public Map<String, String> aliases() {
        return Map.copyOf(aliases);
    }

    public Optional<String> alias(String name) {
        return Optional.ofNullable(aliases.get(name));
    }

    public boolean hasAlias(String name) {
        return aliases.containsKey(name);
    }

    public void setAlias(String name, String connection) {
        aliases.put(name, connection);
        save(configDir.resolve("aliases.json"), aliases, false);
    }

    public void removeAlias(String name) {
        if (!aliases.containsKey(name)) {
            throw new MellowException("alias '" + name + "' does not exist");
        }
        aliases.remove(name);
        save(configDir.resolve("aliases.json"), aliases, false);
    }

    /** Resolves {@code target} as an alias, or returns it unchanged. */
    public String resolveTarget(String target) {
        if (target.contains("@")) {
            return target;
        }
        return aliases.getOrDefault(target, target);
    }

    // --- secrets -----------------------------------------------------------

    public Optional<String> secret(String name) {
        return Optional.ofNullable(secrets.get(name));
    }

    public void setSecret(String name, String value) {
        secrets.put(name, value);
        save(configDir.resolve("secrets.json"), secrets, true);
    }

    /**
     * Password lookup for an alias, mirroring the environment/secret fallback
     * used by the Go CLI.
     */
    public Optional<String> passwordFor(String aliasOrTarget) {
        return secret(aliasOrTarget)
                .or(() -> secret(aliasOrTarget + "_ssh"))
                .or(() -> envPassword(aliasOrTarget));
    }

    private static Optional<String> envPassword(String alias) {
        String normalized = alias.toUpperCase().replace('-', '_');
        String value = System.getenv("SSH_PWD_" + normalized);
        return Optional.ofNullable(value);
    }
}