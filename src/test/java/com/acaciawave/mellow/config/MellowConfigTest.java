package com.acaciawave.mellow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.acaciawave.mellow.MellowException;

class MellowConfigTest {

    @TempDir
    Path dir;

    @Test
    void aliasRoundTrip() {
        MellowConfig config = new MellowConfig(dir);
        config.setAlias("ovh", "ubuntu@1.2.3.4");

        MellowConfig reloaded = new MellowConfig(dir);
        assertThat(reloaded.alias("ovh")).contains("ubuntu@1.2.3.4");
        assertThat(reloaded.hasAlias("ovh")).isTrue();
    }

    @Test
    void resolveTargetPrefersAliasThenPassthrough() {
        MellowConfig config = new MellowConfig(dir);
        config.setAlias("ovh", "ubuntu@1.2.3.4");

        assertThat(config.resolveTarget("ovh")).isEqualTo("ubuntu@1.2.3.4");
        assertThat(config.resolveTarget("root@9.9.9.9")).isEqualTo("root@9.9.9.9");
        assertThat(config.resolveTarget("unknown")).isEqualTo("unknown");
    }

    @Test
    void secretsPersistAndResolve() {
        MellowConfig config = new MellowConfig(dir);
        config.setSecret("ovh", "s3cr3t");

        MellowConfig reloaded = new MellowConfig(dir);
        assertThat(reloaded.secret("ovh")).contains("s3cr3t");
        assertThat(reloaded.passwordFor("ovh")).contains("s3cr3t");
    }

    @Test
    void removingMissingAliasFails() {
        MellowConfig config = new MellowConfig(dir);
        assertThatThrownBy(() -> config.removeAlias("nope"))
                .isInstanceOf(MellowException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void secretsFileIsOwnerOnly() throws IOException {
        if (!Files.getFileStore(dir).supportsFileAttributeView(PosixFileAttributeView.class)) {
            return;
        }
        MellowConfig config = new MellowConfig(dir);
        config.setSecret("ovh", "s3cr3t");

        Set<PosixFilePermission> permissions =
                Files.getPosixFilePermissions(dir.resolve("secrets.json"));
        assertThat(permissions).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE);
    }
}