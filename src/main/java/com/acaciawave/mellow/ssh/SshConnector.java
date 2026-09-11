package com.acaciawave.mellow.ssh;

import com.acaciawave.mellow.MellowException;

/** Fail-fast factory for connections (port of {@code ssh.Connect}). */
public final class SshConnector {

    private SshConnector() {
    }

    public static Connection connect(SshConfig config) {
        SshConnection connection = new SshConnection(config);
        if (!connection.connect()) {
            throw new MellowException(
                    "failed to connect to " + config.user() + "@" + config.host() + ":" + config.port());
        }
        return connection;
    }
}