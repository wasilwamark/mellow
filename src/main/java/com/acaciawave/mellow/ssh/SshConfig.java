package com.acaciawave.mellow.ssh;

import java.time.Duration;

/** SSH connection settings. */
public record SshConfig(
        String host,
        String user,
        int port,
        String password,
        String identityFile,
        Duration timeout) {

    public SshConfig {
        if (port == 0) {
            port = 22;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(15);
        }
    }

    public static SshConfig of(String user, String host, int port, String password) {
        return new SshConfig(host, user, port, password, null, Duration.ofSeconds(15));
    }
}