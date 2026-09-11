package com.acaciawave.mellow.command.keycloak;

import java.security.SecureRandom;
import java.util.List;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.CommandHandler;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.ssh.Connection;
import com.acaciawave.mellow.util.Output;

/** Keycloak identity provider (port of {@code internal/services/keycloak}). */
public final class KeycloakProvider implements Provider {

    static final String KC_DIR = "/opt/keycloak";
    private static final String KC_IMAGE = "quay.io/keycloak/keycloak:23.0.0";
    private static final String PG_IMAGE = "postgres:15";
    private static final String KCADM = "/opt/keycloak/bin/kcadm.sh";
    private static final String KC_CLI_CONFIG = "/opt/keycloak/conf/keycloak-cli.properties";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String name() {
        return "keycloak";
    }

    @Override
    public String description() {
        return "Keycloak identity and access management service";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.builder("install", "Install Keycloak with Docker and PostgreSQL", this::install)
                        .arg(Argument.optional("domain", "Domain (default keycloak.local)"))
                        .build(),
                Command.builder("uninstall", "Remove Keycloak installation", this::uninstall)
                        .arg(Argument.optional("domain", "Domain used during install"))
                        .build(),
                Command.of("start", "Start Keycloak services", serviceAction("start")),
                Command.of("stop", "Stop Keycloak services", serviceAction("stop")),
                Command.of("restart", "Restart Keycloak services", serviceAction("restart")),
                Command.of("status", "Check Keycloak service status", this::status),
                Command.builder("logs", "View Keycloak service logs", this::logs)
                        .arg(Argument.optional("service", "keycloak or keycloak-db"))
                        .build(),
                Command.builder("realm", "Manage Keycloak realms (create/list/delete)", this::realm)
                        .arg(Argument.required("action", "create, list or delete"))
                        .arg(Argument.optional("name", "Realm name"))
                        .build(),
                Command.builder("user", "Manage Keycloak users (create/list/reset-password)", this::user)
                        .arg(Argument.required("action", "create, list or reset-password"))
                        .arg(Argument.optional("username", "User name"))
                        .arg(Argument.optional("realm", "Realm (default master)"))
                        .build(),
                Command.builder("client", "Manage Keycloak clients", this::client)
                        .arg(Argument.required("action", "create or list"))
                        .arg(Argument.optional("clientName", "Client id"))
                        .arg(Argument.optional("realm", "Realm (default master)"))
                        .build(),
                Command.builder("ssl", "Configure SSL certificates", this::ssl)
                        .arg(Argument.optional("domain", "Domain (default keycloak.local)"))
                        .build(),
                Command.of("backup", "Backup Keycloak configuration and data", this::backup),
                Command.builder("restore", "Restore Keycloak from backup", this::restore)
                        .arg(Argument.required("backupFile", "Path to backup file"))
                        .build(),
                Command.of("configure", "Interactive configuration management", this::configure));
    }

    // --- lifecycle ---------------------------------------------------------

    private CommandHandler serviceAction(String action) {
        return context -> {
            Output.plain("⚙️  " + capitalize(action) + "ing Keycloak services...");
            CommandResult result = ServiceSupport.runPrivileged(context,
                    "cd " + KC_DIR + " && docker-compose " + action);
            if (!result.success()) {
                throw new MellowException("failed to " + action + " Keycloak services: " + result.stderr());
            }
            Output.success("Keycloak services " + action + "ed successfully");
        };
    }

    private void install(CommandContext context) {
        String password = ServiceSupport.password(context);
        Output.plain("🔐 Installing Keycloak...");

        String domain = context.arg(0) == null ? "keycloak.local" : context.arg(0);

        if (context.connection().runCommand("test -d " + KC_DIR, false).success()) {
            Output.warn("Keycloak is already installed");
            Output.plain("Installation directory: " + KC_DIR);
            return;
        }

        Output.plain("🔍 Checking dependencies...");
        if (!context.connection().runCommand("docker --version", false).success()) {
            throw new MellowException("Docker is not installed. Please install Docker first: mellow docker install");
        }
        if (!context.connection().runCommand("docker-compose --version", false).success()) {
            throw new MellowException("Docker Compose is not installed. Please install Docker Compose first");
        }

        String dbPassword = generateRandomPassword(32);
        String adminPassword = generateRandomPassword(32);

        Output.plain("📁 Creating installation directory...");
        CommandResult mkdir = ServiceSupport.runPrivileged(context, "mkdir -p " + KC_DIR);
        if (!mkdir.success()) {
            throw new MellowException("failed to create installation directory: " + mkdir.stderr());
        }

        Output.plain("📝 Creating Docker Compose configuration...");
        context.connection().writeFile(dockerCompose(dbPassword, adminPassword, domain), KC_DIR + "/docker-compose.yml");

        CommandResult chown = ServiceSupport.runPrivileged(context, "chown -R $USER:$USER " + KC_DIR);
        if (!chown.success()) {
            throw new MellowException("failed to set ownership: " + chown.stderr());
        }

        Output.plain("🚀 Starting Keycloak services...");
        CommandResult up = ServiceSupport.runPrivileged(context, "cd " + KC_DIR + " && docker-compose up -d");
        if (!up.success()) {
            throw new MellowException("failed to start services: " + up.stderr());
        }

        Output.plain("⏳ Waiting for Keycloak to start...");
        waitForKeycloakReady(context.connection());

        Output.plain("🌐 Configuring Nginx reverse proxy...");
        String nginxConfigPath = "/etc/nginx/sites-available/" + domain;
        String tempNginxPath = "/tmp/" + domain + ".conf";
        context.connection().writeFile(nginxSite(domain), tempNginxPath);

        for (String cmd : List.of(
                "mv " + tempNginxPath + " " + nginxConfigPath,
                "ln -sf " + nginxConfigPath + " /etc/nginx/sites-enabled/")) {
            CommandResult result = ServiceSupport.runPrivileged(context, cmd);
            if (!result.success()) {
                throw new MellowException("failed to configure nginx: " + result.stderr());
            }
        }

        CommandResult test = ServiceSupport.runPrivileged(context, "nginx -t");
        if (!test.success()) {
            Output.warn("Nginx config test failed, removing configuration...");
            ServiceSupport.runPrivileged(context, "rm -f " + nginxConfigPath + " /etc/nginx/sites-enabled/" + domain);
            throw new MellowException("nginx configuration error: " + test.stderr());
        }

        CommandResult reload = ServiceSupport.runPrivileged(context, "systemctl reload nginx");
        if (!reload.success()) {
            throw new MellowException("failed to reload nginx: " + reload.stderr());
        }

        String credentialsFile = KC_DIR + "/credentials.txt";
        try {
            context.connection().writeFile(credentials(domain, adminPassword, dbPassword), credentialsFile);
            CommandResult chmod = ServiceSupport.runPrivileged(context, "chmod 600 " + credentialsFile);
            if (!chmod.success()) {
                Output.warn("Failed to set credentials file permissions");
            }
        } catch (MellowException e) {
            Output.warn("Failed to save credentials file: " + e.getMessage());
        }

        Output.success("Keycloak installed successfully!");
        Output.plain("");
        Output.plain("🎉 Installation Complete!");
        Output.plain("📁 Installation Directory: " + KC_DIR);
        Output.plain("🌐 Access URL: http://" + domain);
        Output.plain("👤 Admin Console: http://" + domain + "/admin");
        Output.plain("🔑 Admin Credentials saved to: " + credentialsFile);
        Output.plain("");
        Output.warn("Important:");
        Output.plain("- Store the admin password securely");
        Output.plain("- Configure SSL after installation: mellow keycloak ssl " + domain);
        Output.plain("- Update DNS to point " + domain + " to this server");
    }

    private void uninstall(CommandContext context) {
        Output.plain("🗑️  Uninstalling Keycloak...");

        if (!context.connection().runCommand("test -d " + KC_DIR, false).success()) {
            throw new MellowException("Keycloak is not installed");
        }

        Output.plain("🛑 Stopping and removing containers...");
        CommandResult down = ServiceSupport.runPrivileged(context, "cd " + KC_DIR + " && docker-compose down -v");
        if (!down.success()) {
            Output.warn("Failed to stop containers: " + down.stderr());
        }

        Output.plain("🌐 Removing Nginx configuration...");
        String domain = context.arg(0) == null ? "keycloak.local" : context.arg(0);
        ServiceSupport.runPrivileged(context, "rm -f /etc/nginx/sites-enabled/" + domain);
        ServiceSupport.runPrivileged(context, "rm -f /etc/nginx/sites-available/" + domain);
        ServiceSupport.runPrivileged(context, "systemctl reload nginx");

        Output.plain("📁 Removing installation directory...");
        CommandResult rm = ServiceSupport.runPrivileged(context, "rm -rf " + KC_DIR);
        if (!rm.success()) {
            Output.warn("Failed to remove installation directory: " + rm.stderr());
        }

        Output.plain("🐳 Cleaning up Docker resources...");
        context.connection().runCommand("docker rmi " + KC_IMAGE + " 2>/dev/null || true", false);
        context.connection().runCommand("docker rmi " + PG_IMAGE + " 2>/dev/null || true", false);
        context.connection().runCommand("docker volume rm keycloak_keycloak_db_data 2>/dev/null || true", false);

        Output.success("Keycloak uninstalled successfully!");
    }

    private void status(CommandContext context) {
        Output.plain("🔍 Checking Keycloak status...");

        if (!context.connection().runCommand("test -d " + KC_DIR, false).success()) {
            throw new MellowException("Keycloak is not installed");
        }

        Output.plain("");
        Output.plain("📦 Service Status:");
        CommandResult ps = context.connection().runCommand("cd " + KC_DIR + " && docker-compose ps", false);
        if (!ps.success()) {
            Output.plain("❌ Failed to get service status: " + ps.stderr());
        } else {
            Output.plain(ps.stdout());
        }

        Output.plain("");
        Output.plain("🏥 Health Status:");
        CommandResult health = context.connection().runCommand("cd " + KC_DIR
                + " && docker-compose exec -T keycloak curl -f http://localhost:8080/health/ready 2>/dev/null "
                + "&& echo 'Healthy' || echo 'Unhealthy'", false);
        if (health.success() && health.stdout().contains("Healthy")) {
            Output.plain("🟢 Keycloak is healthy and ready");
        } else if (health.success()) {
            Output.plain("🟡 Keycloak is running but not ready");
        } else {
            Output.plain("🔴 Keycloak is not responding");
        }

        Output.plain("");
        Output.plain("🌐 Access Information:");
        CommandResult hostname = context.connection().runCommand("hostname -f", false);
        if (hostname.success()) {
            String host = hostname.stdout().strip();
            Output.plain("Admin Console: http://" + host + "/admin");
            Output.plain("Base URL: http://" + host);
        }

        String credentialsFile = KC_DIR + "/credentials.txt";
        CommandResult credentialsCheck = context.connection().runCommand(
                "test -f " + credentialsFile + " && echo 'Found' || echo 'Not found'", false);
        if (credentialsCheck.stdout().contains("Found")) {
            Output.plain("🔑 Credentials: " + credentialsFile);
        } else {
            Output.warn("Credentials file not found");
        }

        Output.plain("");
        Output.plain("📊 Resource Usage:");
        CommandResult stats = context.connection().runCommand("cd " + KC_DIR
                + " && docker stats --no-stream --format 'table {{.Container}}\\t{{.CPUPerc}}\\t{{.MemUsage}}'", false);
        if (stats.success()) {
            Output.plain(stats.stdout());
        }
    }

    private void logs(CommandContext context) {
        Output.plain("📜 Streaming Keycloak logs (Ctrl+C to stop)...");
        String service = context.arg(0) == null ? "keycloak" : context.arg(0);
        if (!List.of("keycloak", "keycloak-db").contains(service)) {
            throw new MellowException("invalid service: " + service + ". Valid services: keycloak, keycloak-db");
        }
        context.connection().runInteractive("cd " + KC_DIR + " && docker-compose logs -f " + service);
    }

    // --- realm / user / client --------------------------------------------

    private void realm(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: realm <create|list|delete> [realm-name]");
        }
        String action = context.arg(0);
        switch (action) {
            case "list" -> context.connection().runInteractive(kcadm(context, "get realms"));
            case "create" -> {
                if (context.argCount() < 2) {
                    throw new MellowException("usage: realm create <realm-name>");
                }
                Output.plain("🏗️  Creating realm: " + context.arg(1));
                context.connection().runInteractive(
                        kcadm(context, "create realms -s realm=" + context.arg(1) + " -s enabled=true"));
            }
            case "delete" -> {
                if (context.argCount() < 2) {
                    throw new MellowException("usage: realm delete <realm-name>");
                }
                Output.plain("🗑️  Deleting realm: " + context.arg(1));
                context.connection().runInteractive(kcadm(context, "delete realms/" + context.arg(1)));
            }
            default -> throw new MellowException("unknown action: " + action + ". Use: create, list, delete");
        }
    }

    private void user(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: user <create|list|reset-password> [username] [realm]");
        }
        String action = context.arg(0);
        String realm = context.arg(2) == null ? "master" : context.arg(2);

        switch (action) {
            case "list" -> {
                Output.plain("📋 Listing users in realm '" + realm + "'...");
                context.connection().runInteractive(kcadm(context, "get users -r " + realm));
            }
            case "create" -> {
                if (context.argCount() < 2) {
                    throw new MellowException("usage: user create <username> [realm]");
                }
                String username = context.arg(1);
                String generated = generateRandomPassword(12);
                Output.plain("👤 Creating user: " + username);
                String command = "create users -r " + realm + " -s username=" + username + " -s enabled=true"
                        + " -s credentials=[{\"type\":\"password\",\"value\":\"" + generated + "\",\"temporary\":false}]";
                context.connection().runInteractive(kcadm(context, command));
                Output.success("User '" + username + "' created with password: " + generated);
            }
            case "reset-password" -> {
                if (context.argCount() < 2) {
                    throw new MellowException("usage: user reset-password <username> [realm]");
                }
                String username = context.arg(1);
                String generated = generateRandomPassword(12);
                Output.plain("🔄 Resetting password for user: " + username);
                Output.plain("New password: " + generated);
                context.connection().runInteractive(kcadm(context,
                        "set-password -r " + realm + " -u " + username + " --new-password=\"" + generated + "\""));
            }
            default -> throw new MellowException("unknown action: " + action + ". Use: create, list, reset-password");
        }
    }

    private void client(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: client <create|list> <client-name> [realm]");
        }
        String action = context.arg(0);
        String realm = context.arg(2) == null ? "master" : context.arg(2);

        switch (action) {
            case "list" -> {
                Output.plain("📋 Listing clients in realm '" + realm + "'...");
                context.connection().runInteractive(kcadm(context, "get clients -r " + realm));
            }
            case "create" -> {
                if (context.argCount() < 2) {
                    throw new MellowException("usage: client create <client-name> [realm]");
                }
                String clientName = context.arg(1);
                Output.plain("🔗 Creating client: " + clientName);
                String command = "create clients -r " + realm + " -s clientId=" + clientName
                        + " -s enabled=true -s publicClient=true -s redirectUris=[\"*\"]";
                context.connection().runInteractive(kcadm(context, command));
            }
            default -> throw new MellowException("unknown action: " + action + ". Use: create, list");
        }
    }

    private static String kcadm(CommandContext context, String args) {
        return "cd " + KC_DIR + " && docker-compose exec -T keycloak " + KCADM + " " + args
                + " --config " + KC_CLI_CONFIG;
    }

    // --- ssl / backup / restore / configure -------------------------------

    private void ssl(CommandContext context) {
        String domain = context.arg(0) == null ? "keycloak.local" : context.arg(0);
        Output.plain("🔒 Configuring SSL for " + domain + "...");

        Output.plain("📦 Installing Certbot...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        CommandResult update = ServiceSupport.runPrivileged(context, pkg.update());
        if (!update.success()) {
            Output.warn("Failed to update packages: " + update.stderr());
        }
        CommandResult install = ServiceSupport.runPrivileged(context, pkg.install("certbot", "python3-certbot-nginx"));
        if (!install.success()) {
            Output.warn("Failed to install certbot: " + install.stderr());
        }

        Output.plain("🔐 Obtaining SSL certificate for " + domain + "...");
        CommandResult certbot = ServiceSupport.runPrivileged(context,
                "certbot --nginx -d " + domain + " --non-interactive --agree-tos --email admin@" + domain);
        if (!certbot.success()) {
            throw new MellowException("failed to obtain SSL certificate: " + certbot.stderr());
        }

        Output.plain("🔧 Updating Nginx configuration for SSL...");
        String nginxConfigPath = "/etc/nginx/sites-available/" + domain;
        context.connection().writeFile(sslNginxSite(domain), "/tmp/" + domain + "-ssl.conf");
        CommandResult mv = ServiceSupport.runPrivileged(context, "mv /tmp/" + domain + "-ssl.conf " + nginxConfigPath);
        if (!mv.success()) {
            throw new MellowException("failed to update nginx config: " + mv.stderr());
        }
        CommandResult test = ServiceSupport.runPrivileged(context, "nginx -t");
        if (!test.success()) {
            throw new MellowException("nginx config test failed: " + test.stderr());
        }
        CommandResult reload = ServiceSupport.runPrivileged(context, "systemctl reload nginx");
        if (!reload.success()) {
            throw new MellowException("failed to reload nginx: " + reload.stderr());
        }

        Output.plain("🔧 Updating Keycloak configuration...");
        ServiceSupport.runPrivileged(context, "cd " + KC_DIR
                + " && sed -i 's/KC_HOSTNAME_STRICT_HTTPS: false/KC_HOSTNAME_STRICT_HTTPS: true/' docker-compose.yml");

        Output.plain("🔄 Restarting Keycloak to apply SSL configuration...");
        CommandResult restart = ServiceSupport.runPrivileged(context, "cd " + KC_DIR + " && docker-compose restart keycloak");
        if (!restart.success()) {
            Output.warn("Failed to restart Keycloak: " + restart.stderr());
        }

        Output.success("SSL configured successfully!");
        Output.plain("🌐 HTTPS URL: https://" + domain);
        Output.plain("🔑 Admin Console: https://" + domain + "/admin");
    }

    private void backup(CommandContext context) {
        Output.plain("💾 Creating Keycloak backup...");
        String backupDir = "/var/backups/keycloak";

        Output.plain("📁 Creating backup directory: " + backupDir);
        CommandResult mkdir = ServiceSupport.runPrivileged(context, "mkdir -p " + backupDir);
        if (!mkdir.success()) {
            throw new MellowException("failed to create backup directory: " + mkdir.stderr());
        }

        CommandResult date = context.connection().runCommand("date '+%Y%m%d_%H%M%S'", false);
        if (!date.success()) {
            throw new MellowException("failed to generate timestamp");
        }
        String timestamp = date.stdout().strip();
        String backupFile = backupDir + "/keycloak_backup_" + timestamp + ".tar.gz";

        Output.plain("💾 Creating backup: " + backupFile);
        CommandResult tar = ServiceSupport.runPrivileged(context, "tar -czf " + backupFile + " " + KC_DIR);
        if (!tar.success()) {
            throw new MellowException("failed to create backup: " + tar.stderr());
        }

        CommandResult chmod = ServiceSupport.runPrivileged(context, "chmod 600 " + backupFile);
        if (!chmod.success()) {
            Output.warn("Failed to set backup permissions");
        }

        CommandResult size = context.connection().runCommand(
                "sudo ls -lh " + backupFile + " | awk '{print $5}'", false);
        if (size.success()) {
            Output.plain("📊 Backup size: " + size.stdout().strip());
        }

        Output.success("Keycloak backup completed successfully!");
        Output.plain("📁 Backup file: " + backupFile);
    }

    private void restore(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: restore <backup-file>");
        }
        String backupFile = context.arg(0);
        Output.plain("🔄 Restoring Keycloak from backup: " + backupFile);

        if (!context.connection().runCommand("test -f " + backupFile, false).success()) {
            throw new MellowException("backup file not found: " + backupFile);
        }

        Output.plain("🛑 Stopping current Keycloak services...");
        CommandResult down = ServiceSupport.runPrivileged(context, "cd " + KC_DIR + " && docker-compose down");
        if (!down.success()) {
            Output.warn("Failed to stop services: " + down.stderr());
        }

        Output.plain("🗑️  Removing current installation...");
        CommandResult rm = ServiceSupport.runPrivileged(context, "rm -rf " + KC_DIR);
        if (!rm.success()) {
            throw new MellowException("failed to remove current installation: " + rm.stderr());
        }

        Output.plain("📂 Extracting backup...");
        CommandResult extract = ServiceSupport.runPrivileged(context, "cd /opt && tar -xzf " + backupFile);
        if (!extract.success()) {
            throw new MellowException("failed to extract backup: " + extract.stderr());
        }

        Output.plain("🚀 Starting restored services...");
        CommandResult up = ServiceSupport.runPrivileged(context, "cd " + KC_DIR + " && docker-compose up -d");
        if (!up.success()) {
            throw new MellowException("failed to start restored services: " + up.stderr());
        }

        Output.plain("⏳ Waiting for services to start...");
        try {
            waitForKeycloakReady(context.connection());
        } catch (MellowException e) {
            Output.warn("Services may need additional time: " + e.getMessage());
        }

        Output.success("Keycloak restore completed successfully!");
    }

    private void configure(CommandContext context) {
        context.connection().runInteractive(CONFIGURE_SCRIPT);
    }

    private static void waitForKeycloakReady(Connection connection) {
        for (int attempt = 0; attempt < 60; attempt++) {
            CommandResult result = connection.runCommand("cd " + KC_DIR
                    + " && docker-compose exec -T keycloak curl -f http://localhost:8080/health/ready 2>/dev/null "
                    + "&& echo \"ready\" || echo \"not_ready\"", false);
            if (result.success() && result.stdout().contains("ready")) {
                return;
            }
            System.out.print(".");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MellowException("interrupted while waiting for Keycloak");
            }
        }
        throw new MellowException("timeout waiting for Keycloak to be ready");
    }

    // --- pure helpers (unit-tested) ---------------------------------------

    static String generateRandomPassword(int length) {
        final String charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(charset.charAt(RANDOM.nextInt(charset.length())));
        }
        return password.toString();
    }

    static String dockerCompose(String dbPassword, String adminPassword, String domain) {
        return """
                version: '3.8'

                services:
                  keycloak-db:
                    image: postgres:15
                    container_name: keycloak-db
                    environment:
                      POSTGRES_DB: keycloak
                      POSTGRES_USER: keycloak
                      POSTGRES_PASSWORD: %s
                    volumes:
                      - keycloak_db_data:/var/lib/postgresql/data
                    networks:
                      - keycloak-network
                    restart: unless-stopped

                  keycloak:
                    image: quay.io/keycloak/keycloak:23.0.0
                    container_name: keycloak
                    command: ["start-dev"]
                    environment:
                      KC_DB: postgres
                      KC_DB_URL_HOST: keycloak-db
                      KC_DB_URL_DATABASE: keycloak
                      KC_DB_USERNAME: keycloak
                      KC_DB_PASSWORD: %s
                      KEYCLOAK_ADMIN: admin
                      KEYCLOAK_ADMIN_PASSWORD: %s
                      KC_HOSTNAME: %s
                      KC_HTTP_ENABLED: true
                      KC_HOSTNAME_STRICT: false
                      KC_HOSTNAME_STRICT_HTTPS: false
                    ports:
                      - "8080:8080"
                    depends_on:
                      - keycloak-db
                    networks:
                      - keycloak-network
                    restart: unless-stopped
                    healthcheck:
                      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
                      interval: 30s
                      timeout: 10s
                      retries: 5

                volumes:
                  keycloak_db_data:

                networks:
                  keycloak-network:
                    driver: bridge
                """.formatted(dbPassword, dbPassword, adminPassword, domain);
    }

    static String nginxSite(String domain) {
        return """
                server {
                    listen 80;
                    server_name %s;

                    location / {
                        proxy_pass http://localhost:8080;
                        proxy_set_header Host $host;
                        proxy_set_header X-Real-IP $remote_addr;
                        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        proxy_set_header X-Forwarded-Proto $scheme;

                        # WebSocket support
                        proxy_http_version 1.1;
                        proxy_set_header Upgrade $http_upgrade;
                        proxy_set_header Connection "upgrade";
                    }
                }
                """.formatted(domain);
    }

    static String sslNginxSite(String domain) {
        return """
                server {
                    listen 80;
                    server_name %s;
                    return 301 https://$server_name$request_uri;
                }

                server {
                    listen 443 ssl http2;
                    server_name %1$s;

                    ssl_certificate /etc/letsencrypt/live/%1$s/fullchain.pem;
                    ssl_certificate_key /etc/letsencrypt/live/%1$s/privkey.pem;
                    include /etc/letsencrypt/options-ssl-nginx.conf;
                    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

                    location / {
                        proxy_pass http://localhost:8080;
                        proxy_set_header Host $host;
                        proxy_set_header X-Real-IP $remote_addr;
                        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        proxy_set_header X-Forwarded-Proto $scheme;

                        # WebSocket support
                        proxy_http_version 1.1;
                        proxy_set_header Upgrade $http_upgrade;
                        proxy_set_header Connection "upgrade";
                    }
                }
                """.formatted(domain);
    }

    static String credentials(String domain, String adminPassword, String dbPassword) {
        return "Keycloak Installation Details\n"
                + "================================\n"
                + "Domain: " + domain + "\n"
                + "Admin Username: admin\n"
                + "Admin Password: " + adminPassword + "\n"
                + "Database Password: " + dbPassword + "\n"
                + "\n"
                + "Admin Console: http://" + domain + "/admin\n"
                + "API Documentation: http://" + domain + "/realms/master/.well-known/openid_configuration\n";
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final String CONFIGURE_SCRIPT = """
            echo "🔧 Keycloak Configuration Menu"
            echo "================================"
            echo ""
            echo "1. View current configuration"
            echo "2. Update admin password"
            echo "3. Change domain"
            echo "4. View service status"
            echo "5. Show access URLs"
            echo "0. Exit"
            echo ""
            read -p "Select option (0-5): " choice

            case $choice in
                1)
                    echo "📋 Current Configuration:"
                    if [ -f /opt/keycloak/docker-compose.yml ]; then
                        echo "Installation Directory: /opt/keycloak"
                        echo "Services:"
                        cd /opt/keycloak && docker-compose ps
                    else
                        echo "Keycloak is not installed"
                    fi
                    ;;
                2)
                    echo "🔑 Updating admin password..."
                    read -s -p "Enter new admin password: " new_password
                    echo
                    cd /opt/keycloak
                    docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update users/$(docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get users -r master -q username=admin --fields id --config /opt/keycloak/conf/keycloak-cli.properties | grep -o '"id":"[^"]*"' | cut -d'"' -f4) -r master -s 'credentials=[{"type":"password","value":"'"$new_password"'","temporary":false}]' --config /opt/keycloak/conf/keycloak-cli.properties
                    echo "✅ Admin password updated"
                    ;;
                3)
                    echo "🌐 Updating domain configuration..."
                    read -p "Enter new domain: " new_domain
                    echo "⚠️  Domain update requires manual reconfiguration"
                    ;;
                4)
                    echo "🔍 Service Status:"
                    cd /opt/keycloak && docker-compose ps
                    ;;
                5)
                    echo "🌐 Access URLs:"
                    hostname=$(hostname -f)
                    echo "Base URL: http://$hostname"
                    echo "Admin Console: http://$hostname/admin"
                    ;;
                0)
                    echo "👋 Exiting..."
                    ;;
                *)
                    echo "❌ Invalid option"
                    ;;
            esac
            """;
}