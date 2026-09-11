package com.acaciawave.mellow.command.nginx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.CommandHandler;
import com.acaciawave.mellow.cli.Flag;
import com.acaciawave.mellow.cli.Prompts;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.util.Output;

/** Nginx web server provider (port of {@code internal/services/nginx}). */
public final class NginxProvider implements Provider {

    @Override
    public String name() {
        return "nginx";
    }

    @Override
    public String description() {
        return "Manage Nginx web server (install, config, ssl)";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.of("install", "Install Nginx", this::install),
                Command.of("status", "Check Nginx status",
                        context -> context.connection().runInteractive("systemctl status nginx")),
                Command.of("start", "Start Nginx", serviceAction("start")),
                Command.of("stop", "Stop Nginx", serviceAction("stop")),
                Command.of("restart", "Restart Nginx", serviceAction("restart")),
                Command.of("reload", "Reload Nginx configuration", serviceAction("reload")),
                Command.builder("logs", "Stream Nginx logs [access|error|both]", this::logs)
                        .arg(Argument.optional("type", "access, error or both"))
                        .build(),
                Command.of("list-sites", "List all configured sites", this::listSites),
                Command.builder("add-site", "Add a new site (reverse proxy)", this::addSite)
                        .arg(Argument.required("domain", "Domain name"))
                        .flag(Flag.withDefault("proxy", "Local proxy port", "3000"))
                        .flag(Flag.string("file", "Local nginx config file to upload"))
                        .flag(Flag.bool("ssl", "Also install an SSL certificate"))
                        .build(),
                Command.builder("remove-site", "Remove a site configuration", this::removeSite)
                        .arg(Argument.required("domain", "Domain name"))
                        .build(),
                Command.builder("install-ssl", "Install SSL certificate using Certbot", this::installSsl)
                        .arg(Argument.optional("domain", "Domain to secure"))
                        .build());
    }

    // --- handlers ----------------------------------------------------------

    private void install(CommandContext context) {
        Output.plain("🌐 Installing Nginx...");
        String password = ServiceSupport.password(context);
        PackageManager pkg = ServiceSupport.packageManager(context.connection());

        String updateCmd = pkg.update();
        ServiceSupport.logCommand(updateCmd);
        CommandResult update = context.connection().runSudo(updateCmd, password);
        if (!update.success()) {
            throw new MellowException("failed to update package lists: " + update.stderr());
        }

        String installCmd = pkg.install("nginx");
        ServiceSupport.logCommand(installCmd);
        CommandResult install = context.connection().runSudo(installCmd, password);
        if (!install.success()) {
            throw new MellowException("failed to install nginx: " + install.stderr());
        }

        Output.success("Nginx installed successfully!");
    }

    private CommandHandler serviceAction(String action) {
        return context -> {
            String password = ServiceSupport.password(context);

            if (action.equals("reload")) {
                Output.plain("🔍 Testing Nginx configuration...");
                CommandResult test = context.connection().runSudo("nginx -t", password);
                if (!test.success()) {
                    throw new MellowException("nginx config test failed:\n" + test.stderr());
                }
            }

            Output.plain("⚙️  Running: systemctl " + action + " nginx...");
            CommandResult result = context.connection().runSudo("systemctl " + action + " nginx", password);
            if (!result.success()) {
                throw new MellowException("failed to " + action + " nginx: " + result.stderr());
            }
            Output.success("Nginx " + action + "ed successfully");
        };
    }

    private void logs(CommandContext context) {
        Output.plain("📜 Streaming Nginx logs (Ctrl+C to stop)...");

        String type = context.arg(0) == null ? "both" : context.arg(0);
        String command = switch (type) {
            case "access" -> {
                Output.plain("📊 Showing access logs...");
                yield "sudo tail -f /var/log/nginx/access.log";
            }
            case "error" -> {
                Output.plain("❌ Showing error logs...");
                yield "sudo tail -f /var/log/nginx/error.log";
            }
            case "both" -> {
                Output.plain("📊 Access logs & ❌ Error logs...");
                boolean hasMultitail = context.connection().runCommand("which multitail", true).success();
                yield hasMultitail
                        ? "sudo multitail /var/log/nginx/access.log /var/log/nginx/error.log"
                        : "sudo tail -f /var/log/nginx/access.log /var/log/nginx/error.log";
            }
            default -> throw new MellowException(
                    "invalid log type: " + type + ". Use 'access', 'error', or 'both'");
        };

        context.connection().runInteractive(command);
    }

    private void listSites(CommandContext context) {
        Output.plain("🔍 Fetching configured sites...");

        CommandResult result = context.connection().runCommand("ls -1 /etc/nginx/sites-enabled/", true);
        if (!result.success()) {
            throw new MellowException("failed to list sites: " + result.stderr());
        }

        List<String> sites = result.lines();
        if (sites.isEmpty()) {
            Output.plain("No sites configured.");
            return;
        }

        Output.plain("");
        Output.plain("📋 Configured Sites:");
        for (String site : sites) {
            if (site.isBlank()) {
                continue;
            }
            String linkType = context.connection()
                    .runCommand("test -L /etc/nginx/sites-enabled/" + site + " && echo 'symlink' || echo 'file'", true)
                    .stdout().strip();
            boolean hasSsl = context.connection()
                    .runCommand("grep -q 'listen.*443' /etc/nginx/sites-enabled/" + site + " && echo 'yes' || echo 'no'", true)
                    .stdout().strip().equals("yes");

            String status = linkType.equals("symlink") ? "✅" : "⚠️";
            Output.plain("  " + status + " " + site + (hasSsl ? " 🔒 SSL" : ""));
        }
    }

    private void addSite(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: add-site <domain> [--proxy <port>] [--file <local-path>] [--ssl]");
        }
        String domain = context.arg(0);
        String proxyPort = context.flag("proxy") == null ? "3000" : context.flag("proxy");
        String localConfigPath = context.flag("file");
        boolean ssl = context.boolFlag("ssl");

        String configContent;
        if (localConfigPath != null && !localConfigPath.isBlank()) {
            Output.plain("📂 Reading local configuration from " + localConfigPath + "...");
            try {
                configContent = Files.readString(Path.of(localConfigPath));
            } catch (IOException e) {
                throw new MellowException("failed to read local config file: " + e.getMessage(), e);
            }
        } else {
            Output.plain("📝 Configuring site " + domain + " (proxying to localhost:" + proxyPort + ")...");
            configContent = reverseProxyConfig(domain, proxyPort);
        }

        if (!context.connection().directoryExists("/etc/nginx/sites-available")) {
            throw new MellowException("Nginx configuration directory not found. Is Nginx installed? "
                    + "Try running: mellow <target> nginx install");
        }

        String tmpPath = "/tmp/nginx_" + domain + ".conf";
        context.connection().writeFile(configContent, tmpPath);
        String confPath = "/etc/nginx/sites-available/" + domain;

        String password = ServiceSupport.password(context);
        for (String command : List.of(
                "mv " + tmpPath + " " + confPath,
                "ln -sf " + confPath + " /etc/nginx/sites-enabled/")) {
            CommandResult result = context.connection().runSudo(command, password);
            if (!result.success()) {
                throw new MellowException("failed step '" + command + "': " + result.stderr());
            }
        }

        Output.plain("🔍 Testing Nginx configuration...");
        CommandResult test = context.connection().runSudo("nginx -t", password);
        if (!test.success()) {
            Output.plain("❌ Config test failed details:\n" + test.stderr());
            Output.plain("🔄 Rolling back changes...");
            context.connection().runSudo("rm -f /etc/nginx/sites-enabled/" + domain, password);
            throw new MellowException("nginx config test failed. Changes rolled back");
        }

        CommandResult reload = context.connection().runSudo("systemctl reload nginx", password);
        if (!reload.success()) {
            throw new MellowException("failed to reload nginx: " + reload.stderr());
        }

        Output.success("Site " + domain + " added and enabled!");

        if (ssl) {
            Output.plain("🔒 Proceeding to SSL installation...");
            installSslDomain(context, domain);
        }
    }

    private void removeSite(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: remove-site <domain>");
        }
        String domain = context.arg(0);
        Output.plain("🗑️  Removing site " + domain + "...");

        String password = ServiceSupport.password(context);
        for (String command : List.of(
                "rm -f /etc/nginx/sites-enabled/" + domain,
                "rm -f /etc/nginx/sites-available/" + domain,
                "nginx -t",
                "systemctl reload nginx")) {
            CommandResult result = context.connection().runSudo(command, password);
            if (!result.success()) {
                throw new MellowException("failed step '" + command + "': " + result.stderr());
            }
        }

        Output.success("Site " + domain + " removed successfully!");
    }

    private void installSsl(CommandContext context) {
        String domain = context.arg(0);
        if (domain == null || domain.isBlank()) {
            domain = selectSiteInteractively(context);
            if (domain == null) {
                return; // cancelled
            }
        }
        installSslDomain(context, domain);
    }

    private String selectSiteInteractively(CommandContext context) {
        Output.plain("🔍 Fetching available sites...");
        CommandResult result = context.connection().runCommand("ls -1 /etc/nginx/sites-enabled/", true);
        if (!result.success()) {
            throw new MellowException("failed to list sites: " + result.stderr());
        }

        List<String> sites = selectableSites(result.stdout());
        if (sites.isEmpty()) {
            throw new MellowException("no sites found in sites-enabled");
        }

        Output.plain("");
        Output.plain("Available sites:");
        for (int i = 0; i < sites.size(); i++) {
            Output.plain("  [" + (i + 1) + "] " + sites.get(i));
        }
        Output.plain("  [0] Cancel");

        Optional<Integer> selection =
                Prompts.readInt(context.terminal(), "\nSelect site to secure (enter number): ", 0, sites.size());
        if (selection.isEmpty() || selection.get() == 0) {
            return null;
        }
        return sites.get(selection.get() - 1);
    }

    private void installSslDomain(CommandContext context, String domain) {
        String password = ServiceSupport.password(context);
        Output.plain("🔒 Installing Certbot and SSL...");

        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        context.connection().runSudo(pkg.update(), password); // best effort
        try {
            context.connection().runSudo(pkg.install("certbot", "python3-certbot-nginx"), password);
        } catch (MellowException e) {
            Output.warn("certbot may already be installed: " + e.getMessage());
        }

        Output.plain("🔐 Obtaining certificate for " + domain + "...");
        context.connection().runInteractive("sudo certbot --nginx -d " + domain);
    }

    // --- pure helpers (unit-tested) ---------------------------------------

    /** Default reverse-proxy server block for {@code add-site}. */
    static String reverseProxyConfig(String domain, String proxyPort) {
        return """
                server {
                    listen 80;
                    server_name %s;

                    location / {
                        proxy_pass http://localhost:%s;
                        proxy_http_version 1.1;
                        proxy_set_header Upgrade $http_upgrade;
                        proxy_set_header Connection 'upgrade';
                        proxy_set_header Host $host;
                        proxy_cache_bypass $http_upgrade;
                    }
                }
                """.formatted(domain, proxyPort);
    }

    /** Filters an {@code ls} listing down to the sites a user may select. */
    static List<String> selectableSites(String listing) {
        List<String> sites = new ArrayList<>();
        if (listing == null) {
            return sites;
        }
        for (String line : listing.split("\n")) {
            String site = line.strip();
            if (!site.isEmpty() && !site.equals("default")) {
                sites.add(site);
            }
        }
        return sites;
    }
}