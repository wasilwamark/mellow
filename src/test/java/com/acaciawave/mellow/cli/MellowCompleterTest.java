package com.acaciawave.mellow.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jline.reader.Candidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;

import com.acaciawave.mellow.Providers;
import com.acaciawave.mellow.config.MellowConfig;

class MellowCompleterTest {

    @TempDir
    Path dir;

    private MellowCompleter completer;

    @BeforeEach
    void setUp() {
        MellowConfig config = new MellowConfig(dir);
        config.setAlias("ovh", "ubuntu@1.2.3.4");
        completer = new MellowCompleter(Providers.registry(config), config);
    }

    private List<String> candidatesFor(String line) {
        ParsedLine parsed = new DefaultParser().parse(line, line.length(), Parser.ParseContext.COMPLETE);
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsed, candidates);
        return candidates.stream().map(Candidate::value).toList();
    }

    @Test
    void completesAliasesAndServicesForFirstWord() {
        List<String> candidates = candidatesFor("ng");
        assertThat(candidates).contains("nginx");

        assertThat(candidatesFor("ov")).contains("ovh");
        assertThat(candidatesFor("al")).contains("alias");
    }

    @Test
    void completesServicesAfterTarget() {
        assertThat(candidatesFor("myserver ng")).contains("nginx", "mysql", "keycloak");
    }

    @Test
    void completesCommandsForSelectedService() {
        assertThat(candidatesFor("myserver nginx ad")).contains("add-site");
        assertThat(candidatesFor("myserver system up")).contains("update", "upgrade", "full-upgrade");
    }

    @Test
    void completesAliasSubcommands() {
        assertThat(candidatesFor("alias ad")).contains("add");
        assertThat(candidatesFor("alias l")).contains("list");
    }
}