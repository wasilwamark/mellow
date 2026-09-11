package com.acaciawave.mellow.command.nginx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.cli.Command;

class NginxProviderTest {

    private final NginxProvider provider = new NginxProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("nginx");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly(
                        "install", "status", "start", "stop", "restart", "reload",
                        "logs", "list-sites", "add-site", "remove-site", "install-ssl");
    }

    @Test
    void addSiteDeclaresProxyFileAndSslFlags() {
        Command addSite = provider.commands().stream()
                .filter(command -> command.name().equals("add-site"))
                .findFirst()
                .orElseThrow();

        assertThat(addSite.arguments()).extracting(com.acaciawave.mellow.cli.Argument::name).containsExactly("domain");
        assertThat(addSite.flags()).extracting(com.acaciawave.mellow.cli.Flag::name)
                .containsExactly("proxy", "file", "ssl");
        assertThat(addSite.flags().stream().filter(f -> f.name().equals("proxy")).findFirst().orElseThrow()
                .defaultValue()).isEqualTo("3000");
    }

    @Test
    void reverseProxyConfigRendersDomainAndPort() {
        String config = NginxProvider.reverseProxyConfig("example.com", "3000");

        assertThat(config).contains("listen 80;");
        assertThat(config).contains("server_name example.com;");
        assertThat(config).contains("proxy_pass http://localhost:3000;");
        assertThat(config).startsWith("server {").endsWith("}\n");
    }

    @Test
    void selectableSitesFiltersEmptyAndDefault() {
        List<String> sites = NginxProvider.selectableSites("default\napi.example.com\n\nshop.example.com\n");
        assertThat(sites).containsExactly("api.example.com", "shop.example.com");
    }

    @Test
    void selectableSitesHandlesNull() {
        assertThat(NginxProvider.selectableSites(null)).isEmpty();
    }
}