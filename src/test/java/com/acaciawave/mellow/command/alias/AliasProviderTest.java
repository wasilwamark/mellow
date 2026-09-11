package com.acaciawave.mellow.command.alias;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.config.MellowConfig;

class AliasProviderTest {

    @TempDir
    Path dir;

    @Test
    void addStoresAliasAndPassword() {
        MellowConfig config = new MellowConfig(dir);
        AliasProvider provider = new AliasProvider(config);

        Command add = provider.commands().stream()
                .filter(command -> command.name().equals("add"))
                .findFirst()
                .orElseThrow();

        Map<String, Object> flags = new HashMap<>();
        flags.put("password", "s3cr3t");
        add.handler().run(new CommandContext(
                "alias", null, List.of("ovh", "ubuntu@1.2.3.4"), flags, null));

        assertThat(provider.name()).isEqualTo("alias");
        assertThat(config.alias("ovh")).contains("ubuntu@1.2.3.4");
        assertThat(config.secret("ovh")).contains("s3cr3t");
    }

    @Test
    void exposesExpectedCommands() {
        AliasProvider provider = new AliasProvider(new MellowConfig(dir));
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactlyInAnyOrder("add", "list", "remove");
    }
}