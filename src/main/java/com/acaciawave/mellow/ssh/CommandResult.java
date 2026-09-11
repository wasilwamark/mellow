package com.acaciawave.mellow.ssh;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Result of a remote command (port of {@code api.Result}). */
public record CommandResult(boolean success, String stdout, String stderr, int exitCode, Duration duration) {

    public static CommandResult ok(String stdout) {
        return new CommandResult(true, stdout, "", 0, Duration.ZERO);
    }

    public static CommandResult failure(String error) {
        return new CommandResult(false, "", error, 1, Duration.ZERO);
    }

    /** Output stream matching the Go {@code Result.String()} helper. */
    public String output() {
        return success ? stdout : stderr;
    }

    public List<String> lines() {
        if (stdout == null || stdout.isBlank()) {
            return List.of();
        }
        return Arrays.asList(stdout.strip().split("\n"));
    }

    public boolean contains(String text) {
        return stdout != null && stdout.contains(text);
    }
}