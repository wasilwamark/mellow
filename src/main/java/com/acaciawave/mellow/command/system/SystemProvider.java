package com.acaciawave.mellow.command.system;

import java.util.List;
import java.util.StringJoiner;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.util.Output;

/** System package management provider (port of {@code internal/services/system}). */
public final class SystemProvider implements Provider {

    @Override
    public String name() {
        return "system";
    }

    @Override
    public String description() {
        return "System management and upgrades";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.of("update", "Update package lists", this::update),
                Command.of("upgrade", "Upgrade installed packages", this::upgrade),
                Command.of("full-upgrade", "Perform full system upgrade", this::fullUpgrade),
                Command.of("autoremove", "Remove unused packages", this::autoremove),
                Command.of("shell", "Open an interactive shell on the server", this::shell),
                Command.builder("install", "Install packages", this::install)
                        .arg(Argument.required("packages", "Package name(s)"))
                        .build(),
                Command.builder("uninstall", "Uninstall packages", this::uninstall)
                        .arg(Argument.required("packages", "Package name(s)"))
                        .build());
    }

    private void update(CommandContext context) {
        Output.plain("🔄 Updating package lists...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        runPrivileged(context, pkg.update());
        Output.success("Package lists updated");
    }

    private void upgrade(CommandContext context) {
        Output.plain("⬆️  Upgrading packages...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        runPrivileged(context, pkg.upgrade());
        Output.success("Packages upgraded");
    }

    private void fullUpgrade(CommandContext context) {
        Output.plain("🚀 Performing full system upgrade...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        runPrivileged(context, pkg.distUpgrade());
        Output.success("System fully upgraded");
    }

    private void autoremove(CommandContext context) {
        Output.plain("🧹 Removing unused packages...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        runPrivileged(context, pkg.autoremove());
        Output.success("Unused packages removed");
    }

    private void shell(CommandContext context) {
        Output.plain("🔌 Opening interactive shell...");
        context.connection().shell();
    }

    private void install(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: install <package1> [package2...]");
        }
        Output.plain("📦 Installing: " + join(context.args()) + "...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        runPrivileged(context, pkg.install(context.args().toArray(String[]::new)));
        Output.success("Installation complete");
    }

    private void uninstall(CommandContext context) {
        if (context.argCount() < 1) {
            throw new MellowException("usage: uninstall <package1> [package2...]");
        }
        Output.plain("🗑️  Uninstalling: " + join(context.args()) + "...");
        PackageManager pkg = ServiceSupport.packageManager(context.connection());
        runPrivileged(context, pkg.remove(context.args().toArray(String[]::new)));
        Output.success("Uninstallation complete");
    }

    private void runPrivileged(CommandContext context, String command) {
        ServiceSupport.logCommand(command);
        var result = context.connection().runSudo(command, ServiceSupport.password(context));
        ServiceSupport.checkSudo(result, context);
    }

    private static String join(List<String> values) {
        StringJoiner joiner = new StringJoiner(" ");
        values.forEach(joiner::add);
        return joiner.toString();
    }
}