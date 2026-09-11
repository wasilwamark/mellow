package com.acaciawave.mellow.cli;

import java.util.Arrays;
import java.util.List;

import org.jline.terminal.Terminal;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.config.MellowConfig;
import com.acaciawave.mellow.util.Output;

/**
 * Mellow's command-line front-end. Handles root-level commands
 * ({@code alias}, {@code help}, {@code version}) and delegates everything else
 * to the {@link Dispatcher} for direct execution.
 */
public final class Cli {

    public static final String VERSION = "0.1.0-java";

    private final Registry registry;
    private final MellowConfig config;
    private final Dispatcher dispatcher;

    public Cli(Registry registry, MellowConfig config) {
        this.registry = registry;
        this.config = config;
        this.dispatcher = new Dispatcher(registry, config);
    }

    public int run(String[] argv, Terminal terminal) {
        if (argv.length == 0) {
            if (terminal != null) {
                new Repl(registry, config, this).start(terminal);
                return 0;
            }
            printRootHelp();
            return 0;
        }

        String first = argv[0];
        switch (first) {
            case "help", "--help", "-h" -> {
                printRootHelp();
                return 0;
            }
            case "version", "--version", "-v" -> {
                System.out.println("mellow version " + VERSION);
                return 0;
            }
            case "alias" -> {
                return runAlias(Arrays.asList(argv).subList(1, argv.length));
            }
            default -> {
                // fall through
            }
        }

        // A known provider used without a target, e.g. `mellow nginx install`.
        if (registry.contains(first)) {
            if (argv.length >= 2 && isHelp(argv[1])) {
                printProviderHelp(registry.get(first).orElseThrow());
                return 0;
            }
            Output.error("service '" + first + "' requires a target: mellow <user@host|alias> " + first + " ...");
            return 1;
        }

        // Direct execution: mellow <target> <provider> <command> [args...]
        if (argv.length < 3) {
            Output.error("usage: mellow <user@host|alias> <service> <command> [args...]");
            return 1;
        }
        if (isHelp(argv[2])) {
            registry.get(argv[1]).ifPresentOrElse(this::printProviderHelp,
                    () -> Output.error("unknown service '" + argv[1] + "'"));
            return 0;
        }

        try {
            dispatcher.runDirect(argv[0], argv[1], argv[2], Arrays.asList(argv).subList(3, argv.length), terminal);
            return 0;
        } catch (MellowException e) {
            Output.error(e.getMessage());
            return 1;
        }
    }

    // --- local alias commands (no SSH) -------------------------------------

    private int runAlias(List<String> args) {
        if (args.isEmpty()) {
            Output.error("usage: mellow alias <add|list|remove> ...");
            return 1;
        }
        String sub = args.get(0);
        return switch (sub) {
            case "add" -> aliasAdd(args.subList(1, args.size()));
            case "list" -> aliasList();
            case "remove", "rm" -> aliasRemove(args.subList(1, args.size()));
            default -> {
                Output.error("unknown alias command '" + sub + "'. Use add, list or remove.");
                yield 1;
            }
        };
    }

    private int aliasAdd(List<String> args) {
        if (args.size() < 2) {
            Output.error("usage: mellow alias add <name> <user@host> [--password <password>]");
            return 1;
        }
        String name = args.get(0);
        String connection = args.get(1);
        config.setAlias(name, connection);

        String password = flagValue(args, "--password");
        if (password != null) {
            config.setSecret(name, password);
            Output.success("Added alias '" + name + "' for " + connection + " (with password saved)");
        } else {
            Output.success("Added alias '" + name + "' for " + connection);
        }
        return 0;
    }

    private int aliasList() {
        var aliases = config.aliases();
        if (aliases.isEmpty()) {
            Output.plain("No aliases found. Use 'mellow alias add' to add one.");
            return 0;
        }
        Output.plain("Server Aliases:");
        aliases.forEach((name, connection) -> Output.plain("  " + name + ": " + connection));
        return 0;
    }

    private int aliasRemove(List<String> args) {
        if (args.isEmpty()) {
            Output.error("usage: mellow alias remove <name>");
            return 1;
        }
        try {
            config.removeAlias(args.get(0));
            Output.success("Removed alias '" + args.get(0) + "'");
            return 0;
        } catch (MellowException e) {
            Output.error(e.getMessage());
            return 1;
        }
    }

    private static String flagValue(List<String> args, String flag) {
        for (int i = 0; i < args.size() - 1; i++) {
            if (args.get(i).equals(flag)) {
                return args.get(i + 1);
            }
            if (args.get(i).startsWith(flag + "=")) {
                return args.get(i).substring(flag.length() + 1);
            }
        }
        return null;
    }

    // --- help --------------------------------------------------------------

    private void printRootHelp() {
        Output.plain("Mellow - Configure your servers with simple commands");
        Output.plain("");
        Output.plain("Usage:");
        Output.plain("  mellow <user@host|alias> <service> <command> [args...]");
        Output.plain("  mellow alias <add|list|remove> ...");
        Output.plain("  mellow                # interactive shell (TAB completion)");
        Output.plain("  mellow help");
        Output.plain("");
        Output.plain("Examples:");
        Output.plain("  mellow myserver system update");
        Output.plain("  mellow myserver nginx install");
        Output.plain("  mellow alias add myserver ubuntu@1.2.3.4 --password 'secret'");
        Output.plain("");
        Output.plain("Services:");
        for (Provider provider : registry.all()) {
            Output.plain(String.format("  %-12s %s", provider.name(), provider.description()));
        }
    }

    private void printProviderHelp(Provider provider) {
        Output.plain(provider.name() + " - " + provider.description());
        Output.plain("");
        Output.plain("Commands:");
        for (Command command : provider.commands()) {
            Output.plain(String.format("  %-14s %s", command.name(), command.description()));
        }
    }

    private static boolean isHelp(String token) {
        return token.equals("--help") || token.equals("-h") || token.equals("help");
    }
}