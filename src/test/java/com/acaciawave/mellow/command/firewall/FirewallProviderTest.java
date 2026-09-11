package com.acaciawave.mellow.command.firewall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.cli.Command;

class FirewallProviderTest {

    private final FirewallProvider provider = new FirewallProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("firewall");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly(
                        "install", "allow", "deny", "status", "enable", "disable", "reset", "delete", "logging");
    }

    @Test
    void installDeclaresDocumentedFlags() {
        Command install = provider.commands().stream()
                .filter(command -> command.name().equals("install"))
                .findFirst()
                .orElseThrow();

        assertThat(install.flags()).extracting(com.acaciawave.mellow.cli.Flag::name)
                .containsExactly("default-policy", "enable-logging", "allow-ssh");
        assertThat(install.flags().stream().filter(f -> f.name().equals("enable-logging")).findFirst().orElseThrow()
                .defaultValue()).isEqualTo("true");
        assertThat(install.flags().stream().filter(f -> f.name().equals("default-policy")).findFirst().orElseThrow()
                .defaultValue()).isNull();
    }

    @Test
    void buildsRuleCommands() {
        assertThat(FirewallProvider.ruleCommand("allow", "80", "", "")).isEqualTo("ufw allow 80");
        assertThat(FirewallProvider.ruleCommand("allow", "80", "tcp", "")).isEqualTo("ufw allow 80/tcp");
        assertThat(FirewallProvider.ruleCommand("allow", "80", "", "10.0.0.1"))
                .isEqualTo("ufw allow from 10.0.0.1 to any port 80");
        assertThat(FirewallProvider.ruleCommand("deny", "22", "tcp", "10.0.0.1"))
                .isEqualTo("ufw deny from 10.0.0.1 to any port 22 proto tcp");
    }
}