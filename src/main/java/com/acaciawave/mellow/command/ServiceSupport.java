package com.acaciawave.mellow.command;

import java.util.Locale;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.distro.DistroInfo;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.pkgmgr.PackageManagers;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.ssh.Connection;
import com.acaciawave.mellow.util.Output;

/** Helpers shared by service providers (ports of the Go per-plugin helpers). */
public final class ServiceSupport {

    private ServiceSupport() {
    }

    public static String password(CommandContext context) {
        String password = context.password();
        return password == null ? "" : password;
    }

    public static void logCommand(String command) {
        Output.step("Executing: " + command);
    }

    /** Prints the detected distro/package manager and returns the manager. */
    public static PackageManager packageManager(Connection connection) {
        DistroInfo distro = connection.distroInfo();
        Output.info("Detected Distribution: " + distro.display());
        Output.info("Using Package Manager: " + distro.packageManager());
        return PackageManagers.forDistro(distro);
    }

    /** Fails with sudo guidance when a privileged command did not succeed. */
    public static void checkSudo(CommandResult result, CommandContext context) {
        if (result.success()) {
            return;
        }
        String message = "failed to execute command: " + result.stderr();
        String stderr = result.stderr() == null ? "" : result.stderr().toLowerCase(Locale.ROOT);
        if (stderr.contains("sudo") || stderr.contains("permission") || stderr.contains("password")) {
            if (password(context).isEmpty()) {
                message += "\n\nRoot privileges required.\n"
                        + "Resolution Tips:\n"
                        + "1. Set environment variable: export SSH_PWD_<ALIAS>='your-password'\n"
                        + "2. OR update the alias with a password:\n"
                        + "   mellow alias add <name> <user@host> --password 'pass'";
            } else {
                message += "\n\nSudo authentication failed. Check your password.";
            }
        }
        throw new MellowException(message);
    }

    /**
     * Runs a privileged command, using a provided sudo password when available
     * and falling back to password-less sudo (NOPASSWD) otherwise.
     */
    public static CommandResult runPrivileged(CommandContext context, String command) {
        String password = password(context);
        return password.isEmpty()
                ? context.connection().runCommand(command, true)
                : context.connection().runSudo(command, password);
    }
}