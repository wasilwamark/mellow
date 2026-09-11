package com.acaciawave.mellow.cli;

/** A positional argument accepted by a {@link Command}. */
public record Argument(String name, String description, boolean required, ArgumentType type) {

    public static Argument required(String name, String description) {
        return new Argument(name, description, true, ArgumentType.STRING);
    }

    public static Argument optional(String name, String description) {
        return new Argument(name, description, false, ArgumentType.STRING);
    }
}