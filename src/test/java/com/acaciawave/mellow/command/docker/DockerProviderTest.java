package com.acaciawave.mellow.command.docker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.cli.Command;

class DockerProviderTest {

    private final DockerProvider provider = new DockerProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("docker");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly(
                        "install", "status", "compose", "ps", "logs", "prune", "verify", "up", "down", "pull");
    }

    @Test
    void logsTakesOptionalContainer() {
        Command logs = provider.commands().stream()
                .filter(command -> command.name().equals("logs"))
                .findFirst()
                .orElseThrow();
        assertThat(logs.arguments()).hasSize(1);
        assertThat(logs.arguments().getFirst().required()).isFalse();
    }
}