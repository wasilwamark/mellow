package com.acaciawave.mellow.command.fail2ban;

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

/** Fail2Ban provider (port of {@code internal/services/fail2ban}). */
public final class Fail2banProvider implements Provider {

    @Override
    public String name() {
        return "fail2ban";
    }

    @Override
    public String description() {
        return "Protect against brute-force attacks";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.of("install", "Install Fail2Ban and configure SSH jail", this::install),
                Command.of("status", "Show service status and jails",
                        context -> context.connection().runInteractive("sudo fail2ban-client status")),
                Command.builder("banned", "List banned IPs (default: sshd jail)", this::banned)
                        .arg(Argument.optional("jail", "Jail name (default sshd)"))
                        .build(),
                Command.builder("unban", "Unban an IP address", this::unban)
                        .arg(Argument.required("ip", "IP address"))
                        .arg(Argument.optional("jail", "Jail name (default sshd)"))
                        .build());
    }

    private void install(CommandContext context) {
        Output.plain("🛡️  Installing Fail2Ban...");
        String password = ServiceSupport.password(context);
        PackageManager pkg = ServiceSupport.packageManager(context.connection());

        CommandResult update = context.connection().runSudo(pkg.update(), password);
        if (!update.success()) {
            throw new MellowException("failed to update package lists: " + update.stderr());
        }

        CommandResult install = context.connection().runSudo(pkg.install("fail2ban"), password);
        if (!install.success()) {
            throw new MellowException("failed to install fail2ban: " + install.stderr());
        }

        // apt fallback, matching the Go implementation.
        CommandResult aptInstall = context.connection().runSudo("apt-get install -y fail2ban", password);
        if (!aptInstall.success()) {
            throw new MellowException("failed to install fail2ban: " + aptInstall.stderr());
        }

        CommandResult enable = context.connection().runSudo("systemctl enable fail2ban", password);
        if (!enable.success()) {
            throw new MellowException("failed to enable fail2ban: " + enable.stderr());
        }
        CommandResult start = context.connection().runSudo("systemctl start fail2ban", password);
        if (!start.success()) {
            throw new MellowException("failed to start fail2ban: " + start.stderr());
        }

        Output.success("Fail2Ban installed and running.");
    }

    private void banned(CommandContext context) {
        String jail = context.arg(0) == null ? "sshd" : context.arg(0);
        Output.plain("🔍 Checking banned IPs for jail '" + jail + "'...");
        context.connection().runInteractive("sudo fail2ban-client status " + jail);
    }

    private void unban(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: unban <ip> [jail]");
        }
        String ip = context.arg(0);
        String jail = context.arg(1) == null ? "sshd" : context.arg(1);
        Output.plain("🔓 Unbanning " + ip + " from " + jail + "...");
        context.connection().runInteractive("sudo fail2ban-client set " + jail + " unbanip " + ip);
    }
}