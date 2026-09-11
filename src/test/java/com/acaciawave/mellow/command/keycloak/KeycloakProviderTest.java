package com.acaciawave.mellow.command.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.cli.Command;

class KeycloakProviderTest {

    private final KeycloakProvider provider = new KeycloakProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("keycloak");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly("install", "uninstall", "start", "stop", "restart", "status", "logs",
                        "realm", "user", "client", "ssl", "backup", "restore", "configure");
    }

    @Test
    void generatesRandomPasswordsOfGivenLength() {
        String password = KeycloakProvider.generateRandomPassword(32);
        assertThat(password).hasSize(32).matches("[A-Za-z0-9]+");
        assertThat(KeycloakProvider.generateRandomPassword(32)).isNotEqualTo(password);
    }

    @Test
    void rendersDockerComposeWithSecrets() {
        String compose = KeycloakProvider.dockerCompose("dbpass", "adminpass", "kc.example.com");

        assertThat(compose).contains("POSTGRES_PASSWORD: dbpass");
        assertThat(compose).contains("KC_DB_PASSWORD: dbpass");
        assertThat(compose).contains("KEYCLOAK_ADMIN_PASSWORD: adminpass");
        assertThat(compose).contains("KC_HOSTNAME: kc.example.com");
    }

    @Test
    void rendersNginxSite() {
        assertThat(KeycloakProvider.nginxSite("kc.example.com"))
                .contains("server_name kc.example.com;")
                .contains("proxy_pass http://localhost:8080;");
    }

    @Test
    void rendersSslNginxSiteWithDomainEverywhere() {
        String config = KeycloakProvider.sslNginxSite("kc.example.com");
        assertThat(config)
                .contains("server_name kc.example.com;")
                .contains("/etc/letsencrypt/live/kc.example.com/fullchain.pem")
                .contains("/etc/letsencrypt/live/kc.example.com/privkey.pem")
                .contains("return 301 https://$server_name$request_uri;");
    }

    @Test
    void rendersCredentialsFile() {
        assertThat(KeycloakProvider.credentials("kc.example.com", "adminpass", "dbpass"))
                .contains("Admin Password: adminpass")
                .contains("Database Password: dbpass")
                .contains("http://kc.example.com/admin");
    }
}