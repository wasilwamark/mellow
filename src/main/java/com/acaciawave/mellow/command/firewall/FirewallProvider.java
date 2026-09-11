package com.acaciawave.mellow.command.firewall;

import java.util.List;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.Flag;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.distro.DistroFamily;
import com.acaciawave.mellow.distro.DistroInfo;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.util.Output;

/** UFW/Firewalld provider (port of {@code internal/services/firewall}). */
public final class FirewallProvider implements Provider {

    private static final List<String> LOG_ACTIONS = List.of("on", "off", "low", "medium", "high", "full");

    @Override
    public String name() {
        return "firewall";
    }

    @Override
    public String description() {
        return "Firewall management using UFW (Uncomplicated Firewall)";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.builder("install", "Install and configure UFW firewall", this::install)
                        .flag(Flag.string("default-policy", "p", "Default firewall policy (allow/deny)"))
                        .flag(Flag.bool("enable-logging", "l", "Enable firewall logging", true))
                        .flag(Flag.bool("allow-ssh", "Automatically allow SSH connections", true))
                        .build(),
                Command.builder("allow", "Allow traffic through firewall", this::allow)
                        .arg(Argument.required("port", "Port number or service name"))
                        .arg(Argument.optional("protocol", "Protocol (tcp/udp)"))
                        .arg(Argument.optional("from", "Source IP address (optional)"))
                        .build(),
                Command.builder("deny", "Deny traffic through firewall", this::deny)
                        .arg(Argument.required("port", "Port number or service name"))
                        .arg(Argument.optional("protocol", "Protocol (tcp/udp)"))
                        .arg(Argument.optional("from", "Source IP address (optional)"))
                        .build(),
                Command.of("status", "Show firewall status and rules", this::status),
                Command.of("enable", "Enable firewall", this::enable),
                Command.of("disable", "Disable firewall", this::disable),
                Command.of("reset", "Reset firewall to default settings", this::reset),
                Command.builder("delete", "Delete firewall rule", this::delete)
                        .arg(Argument.required("rule", "Rule number to delete"))
                        .build(),
                Command.builder("logging", "Configure firewall logging", this::logging)
                        .arg(Argument.required("action", "Logging action (on/off/low/medium/high/full)"))
                        .build());
    }

    // --- handlers ----------------------------------------------------------

    private void install(CommandContext context) {
        String password = ServiceSupport.password(context);
        DistroInfo distro = context.connection().distroInfo();

        Output.info("Detected Distribution: " + distro.display());
        Output.plain("🔥 Installing firewall (detected: " + distro.family() + ")...");

        if (distro.family() == DistroFamily.DEBIAN
                && context.connection().runCommand("ufw --version", true).success()) {
            Output.success("UFW is already installed");
            return;
        }
        if (distro.family() == DistroFamily.REDHAT
                && context.connection().runCommand("firewall-cmd --version", true).success()) {
            Output.success("Firewalld is already installed");
            return;
        }

        Output.plain("Updating package list...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        String updateCmd = pkg.update();
        ServiceSupport.logCommand(updateCmd);
        CommandResult update = context.connection().runSudo(updateCmd, password);
        if (!update.success()) {
            throw new MellowException("failed to update package list: " + update.stderr());
        }

        Output.plain("Installing firewall...");
        String packageName = distro.family() == DistroFamily.REDHAT ? "firewalld" : "ufw";
        String installCmd = pkg.install(packageName);
        ServiceSupport.logCommand(installCmd);
        CommandResult install = context.connection().runSudo(installCmd, password);
        if (!install.success()) {
            throw new MellowException("failed to install firewall: " + install.stderr());
        }

        String defaultPolicy = context.flag("default-policy") == null ? "deny" : context.flag("default-policy");
        Output.plain("Setting default policy to " + defaultPolicy + "...");
        CommandResult policy = context.connection().runSudo("ufw default " + defaultPolicy, password);
        if (!policy.success()) {
            throw new MellowException("failed to set default policy: " + policy.stderr());
        }

        if (context.boolFlag("enable-logging")) {
            Output.plain("Enabling firewall logging...");
            CommandResult logging = context.connection().runSudo("ufw logging on", password);
            if (!logging.success()) {
                throw new MellowException("failed to enable logging: " + logging.stderr());
            }
        }

        if (context.boolFlag("allow-ssh")) {
            Output.plain("Allowing SSH connections...");
            CommandResult ssh = context.connection().runSudo("ufw allow ssh", password);
            if (!ssh.success()) {
                throw new MellowException("failed to allow SSH: " + ssh.stderr());
            }
        }

        Output.success("UFW firewall installed and configured");
        Output.plain("📝 Note: Run 'mellow firewall enable' to activate the firewall");
        Output.warn("Make sure SSH is allowed before enabling the firewall!");
    }

    private void allow(CommandContext context) {
        applyRule(context, "allow");
    }

    private void deny(CommandContext context) {
        applyRule(context, "deny");
    }

    private void applyRule(CommandContext context, String action) {
        if (context.argCount() < 1) {
            throw new MellowException("port or service is required");
        }
        String port = context.arg(0);
        String protocol = context.arg(1) == null ? "" : context.arg(1);
        String from = context.arg(2) == null ? "" : context.arg(2);
        String command = ruleCommand(action, port, protocol, from);
        boolean allowing = action.equals("allow");

        Output.plain((allowing ? "Allowing" : "Denying") + " traffic: " + command);
        CommandResult result = context.connection().runSudo(command, ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to " + action + " traffic: " + result.stderr());
        }
        Output.success((allowing ? "Allowed" : "Denied") + " traffic on " + port);
    }

    private void status(CommandContext context) {
        Output.plain("🔥 Firewall Status:");
        Output.plain("=================");

        if (!context.connection().runCommand("which ufw", true).success()) {
            Output.plain("❌ UFW is not installed");
            Output.plain("   Run 'mellow firewall install' to install UFW");
            return;
        }

        CommandResult verbose = context.connection().runCommand("ufw status verbose", true);
        if (verbose.success()) {
            Output.plain(verbose.stdout());
        } else {
            Output.plain("❌ Failed to get firewall status");
        }

        CommandResult numbered = context.connection().runCommand("ufw status numbered", true);
        if (numbered.success()) {
            Output.plain("");
            Output.plain("📋 Numbered Rules:");
            Output.plain("====================");
            Output.plain(numbered.stdout());
        }
    }

    private void enable(CommandContext context) {
        String password = ServiceSupport.password(context);
        Output.plain("🔥 Enabling firewall...");
        Output.warn("This will activate the firewall!");

        if (!context.connection().runCommand("ufw status | grep '22/tcp'", true).success()) {
            Output.plain("❌ SSH rule not found! Adding SSH rule to prevent lockout...");
            CommandResult ssh = context.connection().runSudo("ufw allow ssh", password);
            if (!ssh.success()) {
                throw new MellowException("failed to add SSH rule: " + ssh.stderr());
            }
            Output.success("SSH rule added");
        }

        CommandResult result = context.connection().runSudo("ufw --force enable", password);
        if (!result.success()) {
            throw new MellowException("failed to enable firewall: " + result.stderr());
        }
        Output.success("Firewall enabled successfully");
    }

    private void disable(CommandContext context) {
        Output.plain("🔥 Disabling firewall...");
        CommandResult result = context.connection().runSudo("ufw disable", ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to disable firewall: " + result.stderr());
        }
        Output.success("Firewall disabled");
    }

    private void reset(CommandContext context) {
        Output.plain("🔥 Resetting firewall to default settings...");
        Output.warn("This will remove all firewall rules!");
        CommandResult result = context.connection().runSudo("ufw --force reset", ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to reset firewall: " + result.stderr());
        }
        Output.success("Firewall reset successfully");
        Output.plain("📝 Note: You'll need to reconfigure the firewall and enable it again");
    }

    private void delete(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("rule number is required");
        }
        String rule = context.arg(0);
        Output.plain("🔥 Deleting firewall rule " + rule + "...");
        CommandResult result = context.connection()
                .runSudo("ufw delete " + rule, ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to delete rule: " + result.stderr());
        }
        Output.success("Deleted firewall rule " + rule);
    }

    private void logging(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("logging action is required (on/off/low/medium/high/full)");
        }
        String action = context.arg(0);
        if (!LOG_ACTIONS.contains(action)) {
            throw new MellowException("invalid logging action. Use: " + String.join(", ", LOG_ACTIONS));
        }
        Output.plain("🔥 Setting firewall logging to " + action + "...");
        CommandResult result = context.connection()
                .runSudo("ufw logging " + action, ServiceSupport.password(context));
        if (!result.success()) {
            throw new MellowException("failed to set logging: " + result.stderr());
        }
        Output.success("Firewall logging set to " + action);
    }

    // --- pure helpers (unit-tested) ---------------------------------------

    /** Builds a UFW allow/deny rule from the optional protocol/source arguments. */
    static String ruleCommand(String action, String port, String protocol, String from) {
        boolean hasProtocol = protocol != null && !protocol.isEmpty();
        boolean hasFrom = from != null && !from.isEmpty();
        if (hasProtocol && hasFrom) {
            return "ufw " + action + " from " + from + " to any port " + port + " proto " + protocol;
        }
        if (hasProtocol) {
            return "ufw " + action + " " + port + "/" + protocol;
        }
        if (hasFrom) {
            return "ufw " + action + " from " + from + " to any port " + port;
        }
        return "ufw " + action + " " + port;
    }
}