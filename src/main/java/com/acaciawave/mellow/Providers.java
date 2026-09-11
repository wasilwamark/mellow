package com.acaciawave.mellow;

import com.acaciawave.mellow.cli.Registry;
import com.acaciawave.mellow.command.alias.AliasProvider;
import com.acaciawave.mellow.command.docker.DockerProvider;
import com.acaciawave.mellow.command.fail2ban.Fail2banProvider;
import com.acaciawave.mellow.command.firewall.FirewallProvider;
import com.acaciawave.mellow.command.keycloak.KeycloakProvider;
import com.acaciawave.mellow.command.mysql.MysqlProvider;
import com.acaciawave.mellow.command.nginx.NginxProvider;
import com.acaciawave.mellow.command.restic.ResticProvider;
import com.acaciawave.mellow.command.runtimes.RuntimesProvider;
import com.acaciawave.mellow.command.system.SystemProvider;
import com.acaciawave.mellow.config.MellowConfig;

/**
 * Provider registration. This is the Java equivalent of
 * {@code cmd/mellow/providers_init.go}: services are registered directly, there
 * is no plugin loader or dynamic discovery.
 */
public final class Providers {

    private Providers() {
    }

    public static Registry registry(MellowConfig config) {
        Registry registry = new Registry();
        registry.register(new AliasProvider(config));
        registry.register(new SystemProvider());
        registry.register(new NginxProvider());
        registry.register(new MysqlProvider());
        registry.register(new DockerProvider());
        registry.register(new Fail2banProvider());
        registry.register(new FirewallProvider());
        registry.register(new ResticProvider());
        registry.register(new RuntimesProvider());
        registry.register(new KeycloakProvider());
        return registry;
    }
}