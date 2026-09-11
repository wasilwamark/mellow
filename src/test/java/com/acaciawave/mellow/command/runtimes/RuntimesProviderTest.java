package com.acaciawave.mellow.command.runtimes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Command;

class RuntimesProviderTest {

    private final RuntimesProvider provider = new RuntimesProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("runtime");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly("install", "list", "use", "remove", "status", "update");
    }

    @Test
    void normalizesGoVersions() {
        assertThat(RuntimesProvider.normalizeGoVersion("21")).isEqualTo("21.22.0");
        assertThat(RuntimesProvider.normalizeGoVersion("1.21")).isEqualTo("1.21.0");
        assertThat(RuntimesProvider.normalizeGoVersion("1.21.5")).isEqualTo("1.21.5");
    }

    @Test
    void normalizesRubyVersions() {
        assertThat(RuntimesProvider.normalizeRubyVersion("3")).isEqualTo("3.3.0");
        assertThat(RuntimesProvider.normalizeRubyVersion("3.2")).isEqualTo("3.2.0");
        assertThat(RuntimesProvider.normalizeRubyVersion("3.2.1")).isEqualTo("3.2.1");
    }

    @Test
    void resolvesJavaInstallCommand() {
        assertThat(RuntimesProvider.javaInstallCommand("21"))
                .isEqualTo("apt-get update 2>/dev/null || true && apt-get install -y openjdk-21-jdk");
        assertThat(RuntimesProvider.javaInstallCommand("8"))
                .contains("openjdk-8-jdk");
        assertThatThrownBy(() -> RuntimesProvider.javaInstallCommand("25"))
                .isInstanceOf(MellowException.class)
                .hasMessageContaining("unsupported Java version");
    }

    @Test
    void buildsPhpInstallCommand() {
        assertThat(RuntimesProvider.phpInstallCommand("8.2"))
                .isEqualTo("apt-get update 2>/dev/null || true && apt-get install -y "
                        + "php8.2 php8.2-cli php8.2-fpm php8.2-mbstring php8.2-xml php8.2-curl");
        assertThat(RuntimesProvider.phpInstallCommand("8")).contains("php8.1 php8.1-cli");
    }
}