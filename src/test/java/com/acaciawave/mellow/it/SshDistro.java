package com.acaciawave.mellow.it;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.acaciawave.mellow.ssh.SshConfig;

/** Builds and describes SSH-enabled real-distro containers for integration tests. */
public final class SshDistro {

    public static final String ROOT_PASSWORD = "mellow";

    private SshDistro() {
    }

    public static GenericContainer<?> container(Distro distro) {
        ImageFromDockerfile image = new ImageFromDockerfile("mellow-it-" + distro.key(), false)
                .withFileFromString("Dockerfile", distro.dockerfile());
        return new GenericContainer<>(image)
                .withExposedPorts(22)
                .withStartupTimeout(Duration.ofMinutes(5))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
    }

    /** {@code root@host:port} target understood by the Mellow CLI. */
    public static String target(GenericContainer<?> container) {
        return "root@" + container.getHost() + ":" + container.getMappedPort(22);
    }

    public static SshConfig config(GenericContainer<?> container) {
        return SshConfig.of("root", container.getHost(), container.getMappedPort(22), ROOT_PASSWORD);
    }
}