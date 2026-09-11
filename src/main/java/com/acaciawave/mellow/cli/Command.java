package com.acaciawave.mellow.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * A named command belonging to a {@link Provider}.
 *
 * <p>Mirrors {@code api.Command} from the Go tree. Build instances with
 * {@link #of(String, String, CommandHandler)} or the {@link Builder}.</p>
 */
public record Command(
        String name,
        String description,
        List<Argument> arguments,
        List<Flag> flags,
        CommandHandler handler) {

    public Command {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        flags = flags == null ? List.of() : List.copyOf(flags);
    }

    public static Command of(String name, String description, CommandHandler handler) {
        return new Command(name, description, List.of(), List.of(), handler);
    }

    public static Builder builder(String name, String description, CommandHandler handler) {
        return new Builder(name, description, handler);
    }

    public static final class Builder {
        private final String name;
        private final String description;
        private final CommandHandler handler;
        private final List<Argument> arguments = new ArrayList<>();
        private final List<Flag> flags = new ArrayList<>();

        private Builder(String name, String description, CommandHandler handler) {
            this.name = name;
            this.description = description;
            this.handler = handler;
        }

        public Builder arg(Argument argument) {
            arguments.add(argument);
            return this;
        }

        public Builder flag(Flag flag) {
            flags.add(flag);
            return this;
        }

        public Command build() {
            return new Command(name, description, arguments, flags, handler);
        }
    }
}