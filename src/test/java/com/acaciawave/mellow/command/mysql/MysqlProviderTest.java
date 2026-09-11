package com.acaciawave.mellow.command.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.cli.Command;

class MysqlProviderTest {

    private final MysqlProvider provider = new MysqlProvider();

    @Test
    void exposesExpectedCommands() {
        assertThat(provider.name()).isEqualTo("mysql");
        assertThat(provider.commands()).extracting(Command::name)
                .containsExactly("install", "create-db", "create-user", "grant", "status");
    }

    @Test
    void buildsDatabaseStatements() {
        assertThat(MysqlProvider.createDbSql("shop"))
                .isEqualTo("mysql -u root -e 'CREATE DATABASE IF NOT EXISTS shop"
                        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'");
        assertThat(MysqlProvider.createUserSql("app", "p4ss"))
                .isEqualTo("mysql -u root -e \"CREATE USER IF NOT EXISTS 'app'@'localhost' IDENTIFIED BY 'p4ss';\"");
        assertThat(MysqlProvider.grantSql("app", "shop"))
                .contains("GRANT ALL PRIVILEGES ON shop.* TO 'app'@'localhost'")
                .contains("FLUSH PRIVILEGES;");
    }

    @Test
    void secureSqlLocksDownDefaults() {
        assertThat(MysqlProvider.secureSql())
                .contains("DELETE FROM mysql.user WHERE User=''")
                .contains("DROP DATABASE IF EXISTS test")
                .contains("FLUSH PRIVILEGES;");
    }
}