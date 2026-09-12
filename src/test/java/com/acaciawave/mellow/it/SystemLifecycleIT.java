package com.acaciawave.mellow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.containers.GenericContainer;

import com.acaciawave.mellow.ssh.Connection;
import com.acaciawave.mellow.ssh.SshConnector;

/**
 * Validates the {@code system} provider end-to-end against real apt, apk and
 * dnf distributions.
 */
class SystemLifecycleIT extends IntegrationSupport {

    @TestFactory
    Stream<DynamicTest> packageLifecycle() {
        return selectedDistros().stream().map(distro -> distroCase(distro, container(distro)));
    }

    private DynamicTest distroCase(Distro distro, GenericContainer<?> container) {
        return dynamicTest(distro.key() + " — update / install / uninstall / autoremove", () -> {
            Path home = Files.createTempDirectory("mellow-it-" + distro.key());
            MellowCli cli = new MellowCli(home);
            String alias = distro.key();
            cli.addServer(alias, SshDistro.target(container), SshDistro.ROOT_PASSWORD);

            MellowCli.Result update = cli.run(alias, "system", "update");
            assertThat(update.crashed()).as(update.output()).isFalse();
            assertThat(update.success()).as(update.output()).isTrue();

            MellowCli.Result install = cli.run(alias, "system", "install", "tree");
            assertThat(install.crashed()).as(install.output()).isFalse();
            assertThat(install.success()).as(install.output()).isTrue();

            try (var connection = SshConnector.connect(SshDistro.config(container))) {
                assertThat(packageInstalled(distro, connection, "tree"))
                        .as("tree should be installed on " + distro.key())
                        .isTrue();
            }

            MellowCli.Result uninstall = cli.run(alias, "system", "uninstall", "tree");
            assertThat(uninstall.crashed()).as(uninstall.output()).isFalse();
            assertThat(uninstall.success()).as(uninstall.output()).isTrue();

            try (var connection = SshConnector.connect(SshDistro.config(container))) {
                assertThat(packageInstalled(distro, connection, "tree"))
                        .as("tree should be removed on " + distro.key())
                        .isFalse();
            }

            MellowCli.Result autoremove = cli.run(alias, "system", "autoremove");
            assertThat(autoremove.crashed()).as(autoremove.output()).isFalse();
            assertThat(autoremove.success()).as(autoremove.output()).isTrue();
        });
    }

    /**
     * Package presence must be checked with the package manager: on Alpine a
     * busybox symlink (e.g. {@code /usr/bin/tree}) survives package removal, so
     * {@code command -v} is not a reliable signal there.
     */
    private static boolean packageInstalled(Distro distro, Connection connection, String pkg) {
        String command = switch (distro.family()) {
            case APT -> "dpkg -s " + pkg + " 2>/dev/null | grep -q 'install ok installed'";
            case APK -> "apk info -e " + pkg;
            case DNF -> "rpm -q " + pkg;
        };
        return connection.runCommand(command, false).success();
    }
}