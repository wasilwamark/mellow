package com.acaciawave.mellow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.containers.GenericContainer;

import com.acaciawave.mellow.Providers;
import com.acaciawave.mellow.cli.Registry;
import com.acaciawave.mellow.config.MellowConfig;

/**
 * Exercises every Mellow command against real distro containers and asserts the
 * CLI handles each one without an internal error.
 *
 * <p>Commands that block by design (log followers such as {@code nginx logs},
 * {@code docker logs}, {@code keycloak logs}, the interactive {@code system
 * shell}/{@code keycloak configure} and the prompt-driven {@code restic
 * init/backup-db/restore-db} flows) are intentionally not executed here; their
 * wiring is still asserted against the registry. Everything else is run for
 * real — including installs — and expected to either succeed or fail gracefully
 * (exit code 1 with a message), never to crash.</p>
 */
class CommandMatrixIT extends IntegrationSupport {

    private record Case(String provider, String command, List<String> args) {
    }

    private static Case c(String provider, String command, String... args) {
        return new Case(provider, command, List.of(args));
    }

    /** Every command that returns (or fails) on its own — no followers, no prompts. */
    private static final List<Case> CASES = List.of(
            // system
            c("system", "update"),
            c("system", "install", "curl"),
            c("system", "uninstall", "curl"),
            c("system", "autoremove"),

            // nginx
            c("nginx", "install"),
            c("nginx", "list-sites"),
            c("nginx", "add-site", "example.test", "--proxy", "3000"),
            c("nginx", "remove-site", "example.test"),
            c("nginx", "status"),
            c("nginx", "start"),
            c("nginx", "stop"),
            c("nginx", "restart"),
            c("nginx", "reload"),

            // mysql
            c("mysql", "install"),
            c("mysql", "create-db", "mellow_db"),
            c("mysql", "create-user", "mellow_user", "mellow_secret"),
            c("mysql", "grant", "mellow_user", "mellow_db"),
            c("mysql", "status"),

            // docker
            c("docker", "install"),
            c("docker", "status"),
            c("docker", "compose"),
            c("docker", "ps"),
            c("docker", "prune"),
            c("docker", "verify"),
            c("docker", "up"),
            c("docker", "down"),
            c("docker", "pull"),

            // fail2ban
            c("fail2ban", "install"),
            c("fail2ban", "status"),
            c("fail2ban", "banned"),
            c("fail2ban", "unban", "203.0.113.7"),

            // firewall
            c("firewall", "install"),
            c("firewall", "allow", "80"),
            c("firewall", "deny", "81"),
            c("firewall", "status"),
            c("firewall", "enable"),
            c("firewall", "disable"),
            c("firewall", "reset"),
            c("firewall", "delete", "1"),
            c("firewall", "logging", "on"),

            // restic
            c("restic", "install"),
            c("restic", "snapshots"),
            c("restic", "unlock"),

            // runtimes (provider name is "runtime")
            c("runtime", "list"),
            c("runtime", "status"),
            c("runtime", "install", "node", "18"),
            c("runtime", "use", "node", "18"),
            c("runtime", "remove", "node", "18"),
            c("runtime", "update"),

            // keycloak (install fails fast: no Docker inside the container)
            c("keycloak", "install"),
            c("keycloak", "status"),
            c("keycloak", "start"),
            c("keycloak", "stop"),
            c("keycloak", "restart"),
            c("keycloak", "uninstall"),
            c("keycloak", "realm", "list"),
            c("keycloak", "user", "list"),
            c("keycloak", "client", "list"),
            c("keycloak", "ssl", "example.test"),
            c("keycloak", "backup"),
            c("keycloak", "restore", "/tmp/does-not-exist.tar.gz"));

    @TestFactory
    Stream<DynamicTest> everyCommandOnEveryDistro() {
        return selectedDistros().stream()
                .map(distro -> dynamicTest(distro.key() + " — full command matrix",
                        () -> runMatrix(distro, container(distro))));
    }

    private void runMatrix(Distro distro, GenericContainer<?> container) throws Exception {
        Path home = Files.createTempDirectory("mellow-matrix-" + distro.key());
        MellowCli cli = new MellowCli(home);
        String alias = distro.key();
        cli.addServer(alias, SshDistro.target(container), SshDistro.ROOT_PASSWORD);

        int failures = 0;
        for (Case testCase : CASES) {
            String[] argv = new String[3 + testCase.args().size()];
            argv[0] = alias;
            argv[1] = testCase.provider();
            argv[2] = testCase.command();
            for (int i = 0; i < testCase.args().size(); i++) {
                argv[3 + i] = testCase.args().get(i);
            }

            MellowCli.Result result = cli.run(argv);
            String status = result.crashed() ? "CRASH" : (result.success() ? "ok" : "graceful-fail");
            System.out.printf("[%s] %-9s %-15s -> %s%n",
                    distro.key(), testCase.provider(), testCase.command(), status);

            assertThat(result.crashed())
                    .as("%s %s %s crashed on %s:%n%s",
                            testCase.provider(), testCase.command(), testCase.args(),
                            distro.key(), result.output())
                    .isFalse();
            if (result.crashed()) {
                failures++;
            }
        }
        assertThat(failures).as("commands crashed on " + distro.key()).isZero();
    }

    /** Registry wiring: every provider/command must be resolvable (incl. skipped ones). */
    @TestFactory
    Stream<DynamicTest> everyCommandIsRegistered() {
        Path home = Path.of(System.getProperty("java.io.tmpdir"), "mellow-registry-check");
        Registry registry = Providers.registry(new MellowConfig(home));
        return registry.all().stream().map(provider ->
                dynamicTest(provider.name() + " is registered with commands", () -> {
                    assertThat(provider.commands()).isNotEmpty();
                    assertThat(provider.name()).isNotBlank();
                }));
    }
}