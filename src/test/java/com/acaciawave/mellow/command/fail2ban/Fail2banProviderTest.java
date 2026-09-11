package com.acaciawave.mellow.command.fail2ban;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.cli.Command;

class Fail2banProviderTest {

    private final Fail2banProvider provider = new Fail2banProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("fail2ban");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly("install", "status", "banned", "unban");
    }
}