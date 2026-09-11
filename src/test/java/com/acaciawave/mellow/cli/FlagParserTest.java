package com.acaciawave.mellow.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.MellowException;

class FlagParserTest {

    private final Command command = Command.builder("add-site", "Add site", context -> {
            })
            .arg(Argument.required("domain", "Domain"))
            .flag(Flag.string("proxy", "Proxy port"))
            .flag(Flag.bool("ssl", "Enable SSL"))
            .build();

    @Test
    void parsesPositionalAndValueFlag() {
        FlagParser.Parsed parsed = FlagParser.parse(command, List.of("example.com", "--proxy", "3000"));

        assertThat(parsed.positional()).containsExactly("example.com");
        assertThat(parsed.flags()).containsEntry("proxy", "3000");
    }

    @Test
    void parsesInlineFlagValue() {
        FlagParser.Parsed parsed = FlagParser.parse(command, List.of("--proxy=8080", "example.com"));
        assertThat(parsed.flags()).containsEntry("proxy", "8080");
    }

    @Test
    void parsesBooleanFlag() {
        FlagParser.Parsed parsed = FlagParser.parse(command, List.of("example.com", "--ssl"));
        assertThat(parsed.flags()).containsEntry("ssl", Boolean.TRUE);
    }

    @Test
    void booleanFlagDefaultsToFalse() {
        FlagParser.Parsed parsed = FlagParser.parse(command, List.of("example.com"));
        assertThat(parsed.flags()).containsEntry("ssl", Boolean.FALSE);
    }

    @Test
    void rejectsUnknownFlag() {
        assertThatThrownBy(() -> FlagParser.parse(command, List.of("--nope", "example.com")))
                .isInstanceOf(MellowException.class)
                .hasMessageContaining("unknown flag");
    }

    @Test
    void rejectsMissingValue() {
        assertThatThrownBy(() -> FlagParser.parse(command, List.of("example.com", "--proxy")))
                .isInstanceOf(MellowException.class)
                .hasMessageContaining("requires a value");
    }
}