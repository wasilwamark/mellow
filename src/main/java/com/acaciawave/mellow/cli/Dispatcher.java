package com.acaciawave.mellow.cli;

import java.util.List;
import java.util.Optional;

import org.jline.terminal.Terminal;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.config.MellowConfig;
import com.acaciawave.mellow.ssh.Connection;
import com.acaciawave.mellow.ssh.SshConfig;
import com.acaciawave.mellow.ssh.SshConnector;

/**
 * Resolves a target + provider + command, opens the SSH connection and runs the
 * handler. Direct port of {@code executeDirectCommand} in the Go CLI.
 */
public final class Dispatcher {

    private final Registry registry;
    private final MellowConfig config;

    public Dispatcher(Registry registry, MellowConfig config) {
        this.registry = registry;
        this.config = config;
    }

    /** {@code mellow <target> <provider> <command> [args...]}. */
    public void runDirect(String rawTarget, String providerName, String commandName, List<String> rawArgs, Terminal terminal) {
        Provider provider = registry.get(providerName)
                .orElseThrow(() -> new MellowException("unknown service '" + providerName + "'"));

        Command command = provider.commands().stream()
                .filter(c -> c.name().equals(commandName))
                .findFirst()
                .orElseThrow(() -> new MellowException(
                        "unknown command '" + commandName + "' for service '" + providerName + "'\n"
                                + availableCommands(provider)));

        FlagParser.Parsed parsed = FlagParser.parse(command, rawArgs);

        String resolved = config.resolveTarget(rawTarget);
        Target target = Target.parse(resolved);

        if (!parsed.flags().containsKey("password")) {
            config.passwordFor(rawTarget).ifPresent(password -> parsed.flags().put("password", password));
        }
        String password = Optional.ofNullable(parsed.flags().get("password")).map(Object::toString).orElse(null);

        SshConfig sshConfig = SshConfig.of(target.user(), target.host(), target.port(), password);
        try (Connection connection = SshConnector.connect(sshConfig)) {
            CommandContext context = new CommandContext(resolved, connection, parsed.positional(), parsed.flags(), terminal);
            command.handler().run(context);
        }
    }

    private static String availableCommands(Provider provider) {
        StringBuilder builder = new StringBuilder("Available commands:\n");
        for (Command command : provider.commands()) {
            builder.append("  ").append(command.name()).append(": ").append(command.description()).append('\n');
        }
        return builder.toString().stripTrailing();
    }

    /** Parsed {@code user@host[:port]} target. */
    public record Target(String user, String host, int port) {

        public static Target parse(String value) {
            int at = value.indexOf('@');
            if (at < 0) {
                throw new MellowException(
                        "invalid target '" + value + "'. Expected 'user@host' or a valid alias.");
            }
            String user = value.substring(0, at);
            String hostPart = value.substring(at + 1);

            String host = hostPart;
            int port = 22;
            int colon = hostPart.lastIndexOf(':');
            if (colon > 0) {
                host = hostPart.substring(0, colon);
                try {
                    port = Integer.parseInt(hostPart.substring(colon + 1));
                } catch (NumberFormatException e) {
                    throw new MellowException("invalid port in target '" + value + "'");
                }
            }
            if (user.isEmpty() || host.isEmpty()) {
                throw new MellowException("invalid target '" + value + "'. Expected 'user@host'.");
            }
            return new Target(user, host, port);
        }
    }
}