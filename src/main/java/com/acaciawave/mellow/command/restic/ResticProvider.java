package com.acaciawave.mellow.command.restic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.Prompts;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.ssh.Connection;
import com.acaciawave.mellow.util.Output;

/** Restic S3 backup provider (port of {@code internal/services/restic}). */
public final class ResticProvider implements Provider {

    private static final String ENV_FILE = "/etc/mellow/restic.env";
    private static final List<String> SYSTEM_DATABASES =
            List.of("information_schema", "performance_schema", "mysql", "sys", "admin", "local", "config");

    @Override
    public String name() {
        return "restic";
    }

    @Override
    public String description() {
        return "Restic Backup Manager (S3)";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.of("install", "Install Restic", this::install),
                Command.of("init", "Initialize S3 Repository", this::init),
                Command.of("backup-db", "Stream Database Backup to Repo", this::backupDb),
                Command.of("snapshots", "List Snapshots",
                        context -> context.connection().runInteractive(
                                "sudo bash -c 'source " + ENV_FILE + " && restic snapshots'")),
                Command.of("restore-db", "Restore Database from Backup", this::restoreDb),
                Command.of("unlock", "Unlock Repository",
                        context -> context.connection().runInteractive(
                                "sudo bash -c 'source " + ENV_FILE + " && restic unlock'")));
    }

    // --- handlers ----------------------------------------------------------

    private void install(CommandContext context) {
        Output.plain("💾 Installing Restic...");
        String password = ServiceSupport.password(context);
        PackageManager pkg = ServiceSupport.packageManager(context.connection());

        CommandResult update = context.connection().runSudo(pkg.update(), password);
        if (!update.success()) {
            throw new MellowException("package update failed: " + update.stderr());
        }
        CommandResult install = context.connection().runSudo(pkg.install("restic"), password);
        if (!install.success()) {
            throw new MellowException("installation failed: " + install.stderr());
        }
        CommandResult aptFallback = context.connection().runSudo("apt-get install -y restic", password);
        if (!aptFallback.success()) {
            throw new MellowException("installation failed: " + aptFallback.stderr());
        }
        Output.success("Restic installed.");
    }

    private void init(CommandContext context) {
        Output.plain("⚙️  Initializing Repository Configuration...");
        String sshPassword = ServiceSupport.password(context);

        String repo = ask(context, "S3 Repository URL (e.g., s3:s3.amazonaws.com/my-bucket): ");
        if (repo.isBlank()) {
            throw new MellowException("repo url required");
        }
        String accessKey = ask(context, "AWS Access Key ID: ");
        if (accessKey.isBlank()) {
            throw new MellowException("access key required");
        }
        String secretKey = ask(context, "AWS Secret Access Key: ");
        if (secretKey.isBlank()) {
            throw new MellowException("secret key required");
        }
        String repoPassword = ask(context, "Repository Password: ");
        if (repoPassword.isBlank()) {
            throw new MellowException("password required");
        }

        repo = formatRepositoryUrl(repo);
        Output.plain("📝 Formatted repository URL: " + repo);

        String envContent = resticEnv(repo, accessKey, secretKey, repoPassword);
        context.connection().runSudo("mkdir -p /etc/mellow", sshPassword);
        context.connection().writeFile(envContent, "/tmp/restic.env");
        context.connection().runSudo("mv /tmp/restic.env " + ENV_FILE, sshPassword);
        context.connection().runSudo("chmod 600 " + ENV_FILE, sshPassword);
        Output.plain("🔒 Credentials saved to " + ENV_FILE);

        Output.plain("🚀 Initializing backend...");
        CommandResult result = context.connection()
                .runSudo("bash -c 'source " + ENV_FILE + " && restic init'", sshPassword);
        if (!result.success()) {
            if (result.stderr().contains("config file already exists")
                    || result.stdout().contains("already initialized")) {
                Output.warn("Repository already initialized.");
            } else {
                throw new MellowException("restic init failed: " + result.stderr());
            }
        } else {
            Output.success("Repository initialized successfully.");
        }
    }

    private void backupDb(CommandContext context) {
        String sshPassword = ServiceSupport.password(context);

        Output.plain("🔍 Scanning for database instances...");
        List<DatabaseInstance> instances = discoverInstances(context.connection(), sshPassword);
        if (instances.isEmpty()) {
            throw new MellowException("no database instances found");
        }

        Output.plain("");
        Output.plain("Found Database Instances:");
        for (int i = 0; i < instances.size(); i++) {
            Output.plain("  [" + (i + 1) + "] " + instances.get(i).describe());
        }
        DatabaseInstance instance = instances.get(askIndex(context, "\nSelect instance (enter number): ", instances.size()));

        String[] credentials = detectCredentials(context.connection(), instance, sshPassword);
        String user = askDefault(context, "Database User [" + credentials[0] + "]: ", credentials[0]);
        String masked = credentials[1].isEmpty() ? "" : "*****";
        String passwordInput = ask(context, "Database Password [" + masked + "]: ");
        String dbPassword = passwordInput.isBlank() ? credentials[1] : passwordInput;

        Output.plain("🔍 Listing databases...");
        List<String> databases = listDatabases(context.connection(), instance, user, dbPassword, sshPassword);
        if (databases.isEmpty()) {
            throw new MellowException("no databases found in this instance");
        }

        Output.plain("");
        Output.plain("Available Databases:");
        for (int i = 0; i < databases.size(); i++) {
            Output.plain("  [" + (i + 1) + "] " + databases.get(i));
        }
        String database = databases.get(askIndex(context, "\nSelect database to backup (enter number): ", databases.size()));

        DatabaseInfo target = new DatabaseInfo(database, instance.engine(), instance.type(),
                instance.containerId(), user, dbPassword);
        performBackup(context.connection(), target, sshPassword);
    }

    private void performBackup(Connection connection, DatabaseInfo target, String sshPassword) {
        Output.plain("📦 Streaming backup of " + target.name() + " (" + target.engine() + ")...");

        String dumpCommand = buildBackupCommand(target);
        String extension = extensionFor(target.engine());
        String fullCommand = "bash -c 'source " + ENV_FILE + " && " + dumpCommand
                + " | restic backup --stdin --stdin-filename " + target.name() + "." + extension + "'";

        CommandResult result = connection.runSudo(fullCommand, sshPassword);
        if (!result.success()) {
            throw new MellowException("backup failed: " + result.stderr());
        }
        Output.success("Database backup completed.");
    }

    private void restoreDb(CommandContext context) {
        String sshPassword = ServiceSupport.password(context);

        Output.plain("📋 Fetching available snapshots...");
        CommandResult result = context.connection()
                .runSudo("bash -c 'source " + ENV_FILE + " && restic snapshots --json'", sshPassword);
        if (!result.success()) {
            throw new MellowException("failed to list snapshots: " + result.stderr());
        }

        List<Snapshot> snapshots = parseSnapshots(result.stdout());
        if (snapshots.isEmpty()) {
            throw new MellowException("no snapshots found");
        }

        Output.plain("");
        Output.plain("Available Backups:");
        for (int i = 0; i < snapshots.size(); i++) {
            Output.plain("  [" + (i + 1) + "] " + snapshots.get(i).display());
        }
        Snapshot snapshot = snapshots.get(askIndex(context, "\nSelect backup to restore (enter number): ", snapshots.size()));

        String filename = snapshot.paths().isEmpty() ? "unknown" : snapshot.paths().get(0);
        String dbName = databaseNameFromFilename(filename);

        String engine;
        if (filename.endsWith(".sql")) {
            engine = askDefault(context, "Database engine (mysql/postgres): ", "postgres");
        } else if (filename.endsWith(".archive")) {
            engine = "mongo";
        } else {
            engine = "postgres";
        }

        Output.plain("");
        Output.plain("🔍 Scanning for database instances...");
        List<DatabaseInstance> matching = discoverInstances(context.connection(), sshPassword).stream()
                .filter(instance -> instance.engine().equals(engine))
                .toList();
        if (matching.isEmpty()) {
            throw new MellowException("no " + engine + " instances found");
        }

        Output.plain("");
        Output.plain("Found " + engine.toUpperCase() + " Instances:");
        for (int i = 0; i < matching.size(); i++) {
            Output.plain("  [" + (i + 1) + "] " + matching.get(i).describe());
        }
        DatabaseInstance instance = matching.get(askIndex(context, "\nSelect target instance (enter number): ", matching.size()));

        String[] credentials = detectCredentials(context.connection(), instance, sshPassword);
        String user = askDefault(context, "Database User [" + credentials[0] + "]: ", credentials[0]);
        String masked = credentials[1].isEmpty() ? "" : "*****";
        String passwordInput = ask(context, "Database Password [" + masked + "]: ");
        String dbPassword = passwordInput.isBlank() ? credentials[1] : passwordInput;

        Output.plain("");
        Output.warn("This will OVERWRITE the '" + dbName + "' database!");
        String confirm = ask(context, "Type 'yes' to confirm: ");
        if (!"yes".equals(confirm)) {
            throw new MellowException("restore cancelled");
        }

        Output.plain("🔄 Restoring " + filename + " to " + engine + " instance...");
        String restoreCommand = buildRestoreCommand(snapshot.id(), filename, engine, instance, user, dbPassword, dbName);
        CommandResult restore = context.connection().runSudo(restoreCommand, sshPassword);
        if (!restore.success()) {
            throw new MellowException("restore failed: " + restore.stderr());
        }
        Output.success("Database restored successfully.");
    }

    // --- discovery ---------------------------------------------------------

    private static List<DatabaseInstance> discoverInstances(Connection connection, String sshPassword) {
        List<DatabaseInstance> instances = new ArrayList<>();
        if (connection.runCommand("which mysql", true).success()) {
            instances.add(new DatabaseInstance("mysql", "host", "", ""));
        }
        if (connection.runCommand("which psql", true).success()) {
            instances.add(new DatabaseInstance("postgres", "host", "", ""));
        }
        if (connection.runCommand("which mongosh", true).success()
                || connection.runCommand("which mongo", true).success()) {
            instances.add(new DatabaseInstance("mongo", "host", "", ""));
        }
        if (connection.runCommand("which docker", true).success()) {
            CommandResult result = connection.runSudo(
                    "docker ps --format '{{.ID}}|{{.Names}}|{{.Image}}'", sshPassword);
            if (result.success()) {
                instances.addAll(parseDockerContainers(result.stdout()));
            }
        }
        return instances;
    }

    private static String[] detectCredentials(Connection connection, DatabaseInstance instance, String sshPassword) {
        String user = "root";
        String password = "";

        if (instance.engine().equals("postgres")) {
            user = "postgres";
        }
        if (instance.engine().equals("mongo")) {
            user = "";
        }

        if (instance.type().equals("docker")) {
            switch (instance.engine()) {
                case "mysql" -> password = getDockerEnv(connection, instance.containerId(), sshPassword,
                        List.of("MYSQL_ROOT_PASSWORD", "MARIADB_ROOT_PASSWORD"));
                case "postgres" -> {
                    password = getDockerEnv(connection, instance.containerId(), sshPassword,
                            List.of("POSTGRES_PASSWORD"));
                    String detectedUser = getDockerEnv(connection, instance.containerId(), sshPassword,
                            List.of("POSTGRES_USER"));
                    if (!detectedUser.isEmpty()) {
                        user = detectedUser;
                    }
                }
                case "mongo" -> {
                    password = getDockerEnv(connection, instance.containerId(), sshPassword,
                            List.of("MONGO_INITDB_ROOT_PASSWORD"));
                    String detectedUser = getDockerEnv(connection, instance.containerId(), sshPassword,
                            List.of("MONGO_INITDB_ROOT_USERNAME"));
                    if (!detectedUser.isEmpty()) {
                        user = detectedUser;
                    }
                }
                default -> {
                }
            }
        }
        return new String[] {user, password};
    }

    private static String getDockerEnv(Connection connection, String containerId, String sshPassword, List<String> keys) {
        String inspect = "docker inspect " + containerId
                + " --format '{{range .Config.Env}}{{println .}}{{end}}'";
        CommandResult result = connection.runSudo(inspect, sshPassword);
        if (!result.success()) {
            return "";
        }
        return dockerEnvValue(result.stdout(), keys);
    }

    private static List<String> listDatabases(Connection connection, DatabaseInstance instance,
                                              String user, String password, String sshPassword) {
        String command = listDatabasesCommand(instance, user, password);
        CommandResult result = connection.runSudo(command, sshPassword);

        if (instance.engine().equals("mongo") && !result.success() && command.contains("mongosh")) {
            result = connection.runSudo(command.replace("mongosh", "mongo"), sshPassword);
        }
        if (!result.success()) {
            throw new MellowException(result.stderr());
        }
        return filterDatabases(result.stdout());
    }

    // --- pure helpers (unit-tested) ---------------------------------------

    static String resticEnv(String repo, String accessKey, String secretKey, String password) {
        return "export RESTIC_REPOSITORY=\"" + repo + "\"\n"
                + "export AWS_ACCESS_KEY_ID=\"" + accessKey + "\"\n"
                + "export AWS_SECRET_ACCESS_KEY=\"" + secretKey + "\"\n"
                + "export RESTIC_PASSWORD=\"" + password + "\"\n";
    }

    static String formatRepositoryUrl(String repo) {
        if (!repo.startsWith("s3:") && !repo.startsWith("/")) {
            return "s3:s3.amazonaws.com/" + repo;
        }
        return repo;
    }

    static List<DatabaseInstance> parseDockerContainers(String output) {
        List<DatabaseInstance> instances = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return instances;
        }
        for (String line : output.strip().split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3) {
                continue;
            }
            String id = parts[0];
            String name = parts[1];
            String image = parts[2];
            if (image.contains("mysql") || image.contains("mariadb")) {
                instances.add(new DatabaseInstance("mysql", "docker", id, name));
            }
            if (image.contains("postgres")) {
                instances.add(new DatabaseInstance("postgres", "docker", id, name));
            }
            if (image.contains("mongo")) {
                instances.add(new DatabaseInstance("mongo", "docker", id, name));
            }
        }
        return instances;
    }

    static String dockerEnvValue(String inspectOutput, List<String> keys) {
        if (inspectOutput == null) {
            return "";
        }
        for (String line : inspectOutput.split("\n")) {
            for (String key : keys) {
                if (line.startsWith(key + "=")) {
                    return line.substring(key.length() + 1);
                }
            }
        }
        return "";
    }

    static boolean isSystemDatabase(String name) {
        return SYSTEM_DATABASES.contains(name);
    }

    static List<String> filterDatabases(String output) {
        List<String> databases = new ArrayList<>();
        if (output == null) {
            return databases;
        }
        for (String line : output.strip().split("\n")) {
            String name = line.strip();
            if (!name.isEmpty() && !isSystemDatabase(name) && !name.contains("template")) {
                databases.add(name);
            }
        }
        return databases;
    }

    static String extensionFor(String engine) {
        return engine.equals("mongo") ? "archive" : "sql";
    }

    static String databaseNameFromFilename(String filename) {
        String name = filename.startsWith("/") ? filename.substring(1) : filename;
        if (name.endsWith(".sql")) {
            name = name.substring(0, name.length() - 4);
        } else if (name.endsWith(".archive")) {
            name = name.substring(0, name.length() - 8);
        }
        return name;
    }

    static List<Snapshot> parseSnapshots(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            List<Snapshot> snapshots = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    String id = node.path("short_id").asText("");
                    String time = node.path("time").asText("");
                    List<String> paths = new ArrayList<>();
                    node.path("paths").forEach(path -> paths.add(path.asText()));
                    snapshots.add(new Snapshot(id, time, paths));
                }
            }
            return snapshots;
        } catch (Exception e) {
            throw new MellowException("failed to parse snapshots: " + e.getMessage(), e);
        }
    }

    static String listDatabasesCommand(DatabaseInstance instance, String user, String password) {
        String passFlag = password == null || password.isEmpty() ? "" : "-p'" + password + "'";
        if (instance.type().equals("host")) {
            return switch (instance.engine()) {
                case "mysql" -> "mysql -u " + user + " " + passFlag + " -N -e 'SHOW DATABASES'";
                case "postgres" -> {
                    if ((password == null || password.isEmpty()) && user.equals("postgres")) {
                        yield "sudo -u postgres psql -l -t -A -F '|' | cut -d'|' -f1";
                    }
                    String env = password == null || password.isEmpty() ? "" : "PGPASSWORD='" + password + "' ";
                    yield env + "psql -U " + user + " -l -t -A -F '|' | cut -d'|' -f1";
                }
                case "mongo" -> "mongosh " + mongoAuth(user, password)
                        + " --quiet --eval 'db.adminCommand( { listDatabases: 1 } ).databases.forEach(db => print(db.name))'";
                default -> "";
            };
        }
        return switch (instance.engine()) {
            case "mysql" -> "docker exec -i " + instance.containerId() + " mysql -u " + user + " " + passFlag
                    + " -N -e 'SHOW DATABASES'";
            case "postgres" -> "docker exec -i -e PGPASSWORD='" + password + "' " + instance.containerId()
                    + " psql -U " + user + " -l -t -A -F '|' | cut -d'|' -f1";
            case "mongo" -> "docker exec -i " + instance.containerId() + " mongosh " + mongoAuth(user, password)
                    + " --quiet --eval 'db.adminCommand( { listDatabases: 1 } ).databases.forEach(db => print(db.name))'";
            default -> "";
        };
    }

    static String buildBackupCommand(DatabaseInfo target) {
        String passFlag = target.password() == null || target.password().isEmpty() ? "" : "-p'" + target.password() + "'";
        return switch (target.engine()) {
            case "mysql" -> target.type().equals("docker")
                    ? "docker exec -i " + target.containerId() + " mysqldump -u " + target.user() + " " + passFlag
                            + " " + target.name()
                    : "mysqldump -u " + target.user() + " " + passFlag
                            + " --single-transaction --quick --lock-tables=false " + target.name();
            case "postgres" -> target.type().equals("docker")
                    ? "docker exec -i -e PGPASSWORD='" + target.password() + "' " + target.containerId()
                            + " pg_dump -U " + target.user() + " " + target.name()
                    : postgresEnvPrefix(target.password()) + "pg_dump -U " + target.user() + " " + target.name();
            case "mongo" -> target.type().equals("docker")
                    ? "docker exec -i " + target.containerId() + " mongodump " + mongoAuth(target.user(), target.password())
                            + " --db " + target.name() + " --archive"
                    : "mongodump " + mongoAuth(target.user(), target.password()) + " --db " + target.name() + " --archive";
            default -> "";
        };
    }

    static String buildRestoreCommand(String snapshotId, String filename, String engine,
                                      DatabaseInstance instance, String user, String password, String dbName) {
        String passFlag = password == null || password.isEmpty() ? "" : "-p'" + password + "'";
        return switch (engine) {
            case "mysql" -> instance.type().equals("docker")
                    ? "bash -c 'source " + ENV_FILE + " && restic dump " + snapshotId + " " + filename
                            + " | docker exec -i " + instance.containerId() + " mysql -u " + user + " " + passFlag + " " + dbName + "'"
                    : "bash -c 'source " + ENV_FILE + " && restic dump " + snapshotId + " " + filename
                            + " | mysql -u " + user + " " + passFlag + " " + dbName + "'";
            case "postgres" -> instance.type().equals("docker")
                    ? "bash -c 'source " + ENV_FILE + " && restic dump " + snapshotId + " " + filename
                            + " | docker exec -i -e PGPASSWORD='" + password + "' " + instance.containerId()
                            + " psql -U " + user + " " + dbName + "'"
                    : "bash -c 'source " + ENV_FILE + " && " + postgresEnvPrefix(password) + "restic dump "
                            + snapshotId + " " + filename + " | psql -U " + user + " " + dbName + "'";
            case "mongo" -> instance.type().equals("docker")
                    ? "bash -c 'source " + ENV_FILE + " && restic dump " + snapshotId + " " + filename
                            + " | docker exec -i " + instance.containerId() + " mongorestore "
                            + mongoAuth(user, password) + " --archive'"
                    : "bash -c 'source " + ENV_FILE + " && restic dump " + snapshotId + " " + filename
                            + " | mongorestore " + mongoAuth(user, password) + " --archive'";
            default -> "";
        };
    }

    private static String mongoAuth(String user, String password) {
        if (user != null && !user.isEmpty() && password != null && !password.isEmpty()) {
            return "--username " + user + " --password '" + password + "' --authenticationDatabase admin";
        }
        return "";
    }

    private static String postgresEnvPrefix(String password) {
        return password == null || password.isEmpty() ? "" : "PGPASSWORD='" + password + "' ";
    }

    // --- interactive helpers ----------------------------------------------

    private static String ask(CommandContext context, String prompt) {
        String value = Prompts.readLine(context.terminal(), prompt);
        return value == null ? "" : value.strip();
    }

    private static String askDefault(CommandContext context, String prompt, String defaultValue) {
        String value = ask(context, prompt);
        return value.isEmpty() ? defaultValue : value;
    }

    private static int askIndex(CommandContext context, String prompt, int count) {
        Optional<Integer> selection = Prompts.readInt(context.terminal(), prompt, 1, count);
        if (selection.isEmpty()) {
            throw new MellowException("invalid selection");
        }
        return selection.get() - 1;
    }

    // --- value types --------------------------------------------------------

    record DatabaseInstance(String engine, String type, String containerId, String containerName) {
        String describe() {
            String source = type.equals("docker") ? "Docker Container (" + containerName + ")" : "Host";
            return String.format("%-10s %s", engine.toUpperCase(), source);
        }
    }

    record DatabaseInfo(String name, String engine, String type, String containerId, String user, String password) {
    }

    record Snapshot(String id, String time, List<String> paths) {
        String display() {
            String timestamp = time.length() >= 19 ? time.substring(0, 19) : time;
            String filename = paths.isEmpty() ? "unknown" : paths.get(0);
            return timestamp + " - " + filename;
        }
    }
}