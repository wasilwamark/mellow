package com.acaciawave.mellow.command.alias;

import java.util.List;

import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.config.MellowConfig;
import com.acaciawave.mellow.util.Output;

/** Alias management provider (local; ignores the SSH connection). */
public final class AliasProvider implements Provider {

    private final MellowConfig config;

    public AliasProvider() {
        this(new MellowConfig());
    }

    public AliasProvider(MellowConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "alias";
    }

    @Override
    public String description() {
        return "Server alias management";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.builder("add", "Add a server alias", this::add)
                        .arg(Argument.required("name", "Alias name"))
                        .arg(Argument.required("connection", "User@host connection string"))
                        .flag(com.acaciawave.mellow.cli.Flag.string("password", "Password to store for this alias"))
                        .build(),
                Command.of("list", "List all server aliases", this::list),
                Command.builder("remove", "Remove a server alias", this::remove)
                        .arg(Argument.required("name", "Alias name to remove"))
                        .build());
    }

    private void add(CommandContext context) {
        if (context.argCount() < 2) {
            throw new com.acaciawave.mellow.MellowException("name and connection are required");
        }
        String name = context.arg(0);
        String connection = context.arg(1);
        config.setAlias(name, connection);

        String password = context.flag("password");
        if (password != null && !password.isEmpty()) {
            config.setSecret(name, password);
        }
        Output.success("Added alias '" + name + "' for " + connection);
    }

    private void list(CommandContext context) {
        var aliases = config.aliases();
        if (aliases.isEmpty()) {
            Output.plain("No aliases found. Use 'mellow alias add' to add one.");
            return;
        }
        Output.plain("Server Aliases:");
        aliases.forEach((name, connection) -> Output.plain("  " + name + ": " + connection));
    }

    private void remove(CommandContext context) {
        if (context.argCount() < 1) {
            throw new com.acaciawave.mellow.MellowException("alias name is required");
        }
        config.removeAlias(context.arg(0));
        Output.success("Removed alias '" + context.arg(0) + "'");
    }
}