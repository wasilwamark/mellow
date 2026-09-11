package com.acaciawave.mellow.command.restic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.cli.Command;

class ResticProviderTest {

    private final ResticProvider provider = new ResticProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("restic");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly("install", "init", "backup-db", "snapshots", "restore-db", "unlock");
    }

    @Test
    void formatsRepositoryUrls() {
        assertThat(ResticProvider.formatRepositoryUrl("my-bucket")).isEqualTo("s3:s3.amazonaws.com/my-bucket");
        assertThat(ResticProvider.formatRepositoryUrl("s3:s3.amazonaws.com/x")).isEqualTo("s3:s3.amazonaws.com/x");
        assertThat(ResticProvider.formatRepositoryUrl("/mnt/backups")).isEqualTo("/mnt/backups");
    }

    @Test
    void rendersResticEnvFile() {
        String env = ResticProvider.resticEnv("s3:b", "AK", "SK", "pw");
        assertThat(env)
                .contains("export RESTIC_REPOSITORY=\"s3:b\"")
                .contains("export AWS_ACCESS_KEY_ID=\"AK\"")
                .contains("export AWS_SECRET_ACCESS_KEY=\"SK\"")
                .contains("export RESTIC_PASSWORD=\"pw\"");
    }

    @Test
    void parsesDockerContainersByImage() {
        String output = """
                abc123|db1|mysql:8
                def456|pg1|postgres:16
                ghi789|web|nginx:latest
                jkl012|mongo1|mongo:7
                """;
        List<ResticProvider.DatabaseInstance> instances = ResticProvider.parseDockerContainers(output);

        assertThat(instances).extracting(ResticProvider.DatabaseInstance::engine)
                .containsExactly("mysql", "postgres", "mongo");
        assertThat(instances.get(0).containerId()).isEqualTo("abc123");
        assertThat(instances.get(0).containerName()).isEqualTo("db1");
    }

    @Test
    void filtersSystemDatabases() {
        assertThat(ResticProvider.isSystemDatabase("mysql")).isTrue();
        assertThat(ResticProvider.isSystemDatabase("shop")).isFalse();
        assertThat(ResticProvider.filterDatabases("mysql\nshop\ntemplate0\napp\n"))
                .containsExactly("shop", "app");
    }

    @Test
    void readsDockerEnvValues() {
        String inspect = "PATH=/usr/bin\nMYSQL_ROOT_PASSWORD=secret\nOTHER=x\n";
        assertThat(ResticProvider.dockerEnvValue(inspect, List.of("MYSQL_ROOT_PASSWORD", "MARIADB_ROOT_PASSWORD")))
                .isEqualTo("secret");
        assertThat(ResticProvider.dockerEnvValue(inspect, List.of("MISSING"))).isEmpty();
    }

    @Test
    void parsesSnapshotsJson() {
        String json = """
                [
                  {"short_id":"a1b2c3","time":"2024-05-01T10:00:00Z","paths":["/shop.sql"]},
                  {"short_id":"d4e5f6","time":"2024-05-02T11:30:00Z","paths":["/app.archive"]}
                ]
                """;
        List<ResticProvider.Snapshot> snapshots = ResticProvider.parseSnapshots(json);

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).id()).isEqualTo("a1b2c3");
        assertThat(snapshots.get(0).display()).startsWith("2024-05-01T10:00:00 - /shop.sql");
    }

    @Test
    void derivesDatabaseNameFromFilename() {
        assertThat(ResticProvider.databaseNameFromFilename("/shop.sql")).isEqualTo("shop");
        assertThat(ResticProvider.databaseNameFromFilename("/app.archive")).isEqualTo("app");
    }

    @Test
    void buildsHostMysqlBackupCommand() {
        ResticProvider.DatabaseInfo target =
                new ResticProvider.DatabaseInfo("shop", "mysql", "host", "", "root", "pw");
        assertThat(ResticProvider.buildBackupCommand(target))
                .isEqualTo("mysqldump -u root -p'pw' --single-transaction --quick --lock-tables=false shop");
    }

    @Test
    void buildsDockerPostgresRestoreCommand() {
        ResticProvider.DatabaseInstance instance =
                new ResticProvider.DatabaseInstance("postgres", "docker", "cid", "pg");
        String command = ResticProvider.buildRestoreCommand("snap1", "/shop.sql", "postgres", instance, "postgres", "pw", "shop");
        assertThat(command)
                .contains("restic dump snap1 /shop.sql")
                .contains("docker exec -i -e PGPASSWORD='pw' cid psql -U postgres shop");
    }
}