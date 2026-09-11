package com.acaciawave.mellow.command.mysql;

import java.util.List;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.util.Output;

/** MySQL/MariaDB provider (port of {@code internal/services/mysql}). */
public final class MysqlProvider implements Provider {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String description() {
        return "Manage MySQL/MariaDB Database Server";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.of("install", "Install MariaDB Server and secure it", this::install),
                Command.builder("create-db", "Create a new database", this::createDb)
                        .arg(Argument.required("dbname", "Database name"))
                        .build(),
                Command.builder("create-user", "Create a new database user", this::createUser)
                        .arg(Argument.required("username", "User name"))
                        .arg(Argument.required("password", "User password"))
                        .build(),
                Command.builder("grant", "Grant privileges to a user on a database", this::grant)
                        .arg(Argument.required("username", "User name"))
                        .arg(Argument.required("dbname", "Database name"))
                        .build(),
                Command.of("status", "Check service status",
                        context -> context.connection().runInteractive("systemctl status mariadb")));
    }

    private void install(CommandContext context) {
        Output.plain("🗄️  Installing MariaDB Server...");
        String password = ServiceSupport.password(context);
        PackageManager pkg = ServiceSupport.packageManager(context.connection());

        String updateCmd = pkg.update();
        ServiceSupport.logCommand(updateCmd);
        CommandResult update = context.connection().runSudo(updateCmd, password);
        if (!update.success()) {
            throw new MellowException("package update failed: " + update.stderr());
        }

        String installCmd = pkg.install("mariadb-server");
        ServiceSupport.logCommand(installCmd);
        CommandResult install = context.connection().runSudo(installCmd, password);
        if (!install.success()) {
            throw new MellowException("installation failed: " + install.stderr());
        }

        // A non-interactive equivalent of mysql_secure_installation.
        Output.plain("🔒 Securing MariaDB...");
        context.connection().writeFile(secureSql(), "/tmp/secure_mysql.sql");
        CommandResult secure = context.connection().runSudo("mysql -u root < /tmp/secure_mysql.sql", password);
        if (!secure.success()) {
            Output.warn("automated security script had issues: " + secure.stderr());
        }
        context.connection().runSudo("rm /tmp/secure_mysql.sql", password);

        Output.success("MariaDB installed and secured.");
    }

    private void createDb(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: create-db <dbname>");
        }
        String dbName = context.arg(0);
        Output.plain("Creating database " + dbName + "...");

        CommandResult result = context.connection().runSudo(createDbSql(dbName), ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to create db: " + result.stderr());
        }
        Output.success("Database " + dbName + " created.");
    }

    private void createUser(CommandContext context) {
        if (context.argCount() < 2) {
            throw new MellowException("usage: create-user <username> <password>");
        }
        String user = context.arg(0);
        String dbPassword = context.arg(1);
        Output.plain("Creating user " + user + "...");

        CommandResult result = context.connection()
                .runSudo(createUserSql(user, dbPassword), ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to create user: " + result.stderr());
        }
        Output.success("User " + user + " created.");
    }

    private void grant(CommandContext context) {
        if (context.argCount() < 2) {
            throw new MellowException("usage: grant <username> <dbname>");
        }
        String user = context.arg(0);
        String dbName = context.arg(1);
        Output.plain("Granting privileges to " + user + " on " + dbName + "...");

        CommandResult result = context.connection()
                .runSudo(grantSql(user, dbName), ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to grant privileges: " + result.stderr());
        }
        Output.success("Privileges granted.");
    }

    // --- pure helpers (unit-tested) ---------------------------------------

    static String secureSql() {
        return """
                DELETE FROM mysql.user WHERE User='';
                DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1', '::1');
                DROP DATABASE IF EXISTS test;
                DELETE FROM mysql.db WHERE Db='test' OR Db='test_%';
                FLUSH PRIVILEGES;
                """;
    }

    static String createDbSql(String dbName) {
        return "mysql -u root -e 'CREATE DATABASE IF NOT EXISTS " + dbName
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'";
    }

    static String createUserSql(String user, String password) {
        return "mysql -u root -e \"CREATE USER IF NOT EXISTS '" + user + "'@'localhost' IDENTIFIED BY '"
                + password + "';\"";
    }

    static String grantSql(String user, String dbName) {
        return "mysql -u root -e \"GRANT ALL PRIVILEGES ON " + dbName + ".* TO '" + user
                + "'@'localhost'; FLUSH PRIVILEGES;\"";
    }
}