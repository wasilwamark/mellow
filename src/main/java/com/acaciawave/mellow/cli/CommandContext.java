package com.acaciawave.mellow.cli;

import java.util.List;
import java.util.Map;

import org.jline.terminal.Terminal;

import com.acaciawave.mellow.ssh.Connection;

/**
 * Everything a {@link CommandHandler} needs to run: the resolved target, an
 * optional SSH connection (null for local commands such as {@code alias}), the
 * positional arguments, parsed flags, and the JLine terminal for prompts.
 */
public record CommandContext(
        String target,
        Connection connection,
        List<String> args,
        Map<String, Object> flags,
        Terminal terminal) {

    public CommandContext {
        args = args == null ? List.of() : List.copyOf(args);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
    }

    /** Positional argument at {@code index}, or {@code null} when absent. */
    public String arg(int index) {
        return index >= 0 && index < args.size() ? args.get(index) : null;
    }

    public int argCount() {
        return args.size();
    }

    /** Raw string value of a flag, or {@code null}. */
    public String flag(String name) {
        Object value = flags.get(name);
        return value == null ? null : value.toString();
    }

    /** Boolean value of a flag, defaulting to {@code false}. */
    public boolean boolFlag(String name) {
        Object value = flags.get(name);
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    /** Password used for both SSH auth and `sudo -S`, when present. */
    public String password() {
        return flag("password");
    }

    /** True when a real SSH connection is attached. */
    public boolean hasConnection() {
        return connection != null;
    }
}