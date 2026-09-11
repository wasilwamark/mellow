package com.acaciawave.mellow.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SshConnectionTest {

    @Test
    void shellQuotesPathsWithSingleQuotes() {
        assertThat(SshConnection.shellQuote("/etc/nginx/sites-available/example.com"))
                .isEqualTo("'/etc/nginx/sites-available/example.com'");
        assertThat(SshConnection.shellQuote("it's")).isEqualTo("'it'\\''s'");
    }

    @Test
    void commandResultExposesLinesAndLookups() {
        CommandResult result = new CommandResult(true, "a\nb\nc\n", "", 0, Duration.ofMillis(5));

        assertThat(result.lines()).containsExactly("a", "b", "c");
        assertThat(result.contains("b")).isTrue();
        assertThat(result.contains("z")).isFalse();
        assertThat(result.output()).isEqualTo("a\nb\nc\n");
    }

    @Test
    void failureResultExposesStderr() {
        CommandResult failure = CommandResult.failure("boom");
        assertThat(failure.success()).isFalse();
        assertThat(failure.stderr()).isEqualTo("boom");
        assertThat(failure.output()).isEqualTo("boom");
        assertThat(failure.lines()).isEmpty();
    }

    @Test
    void sshConfigDefaultsPortAndTimeout() {
        SshConfig config = new SshConfig("host", "user", 0, null, null, null);
        assertThat(config.port()).isEqualTo(22);
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(15));
    }
}