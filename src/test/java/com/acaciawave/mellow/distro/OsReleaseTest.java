package com.acaciawave.mellow.distro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OsReleaseTest {

    @Test
    void parsesUbuntu() {
        String content = """
                NAME="Ubuntu"
                VERSION_ID="22.04"
                ID=ubuntu
                ID_LIKE=debian
                PRETTY_NAME="Ubuntu 22.04.4 LTS"
                """;

        DistroInfo info = OsRelease.detect(content);

        assertThat(info.id()).isEqualTo("ubuntu");
        assertThat(info.version()).isEqualTo("22.04");
        assertThat(info.family()).isEqualTo(DistroFamily.DEBIAN);
        assertThat(info.packageManager()).isEqualTo(PackageManagerType.APT);
        assertThat(info.isUbuntu()).isTrue();
        assertThat(info.display()).isEqualTo("Ubuntu 22.04");
    }

    @Test
    void parsesAlpine() {
        Map<String, String> values = OsRelease.parse("ID=alpine\nNAME=\"Alpine Linux\"\nVERSION_ID=3.20\n");
        DistroInfo info = OsRelease.toDistroInfo(values);

        assertThat(info.family()).isEqualTo(DistroFamily.ALPINE);
        assertThat(info.packageManager()).isEqualTo(PackageManagerType.APK);
    }

    @Test
    void unknownDistroFallsBackSafely() {
        DistroInfo info = OsRelease.detect("ID=plan9\nNAME=Plan9\n");
        assertThat(info.family()).isEqualTo(DistroFamily.UNKNOWN);
        assertThat(info.packageManager()).isEqualTo(PackageManagerType.UNKNOWN);
    }

    @Test
    void toleratesCommentsAndBlankLines() {
        Map<String, String> values = OsRelease.parse("""
                # comment

                ID=debian
                FOO='single-quoted'
                """);
        assertThat(values).containsEntry("ID", "debian").containsEntry("FOO", "single-quoted");
    }
}