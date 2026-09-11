package com.acaciawave.mellow.cli;

/**
 * A named flag accepted by a {@link Command} (e.g. {@code --password},
 * {@code --proxy 3000}). Values are kept as strings; boolean flags take no value.
 */
public record Flag(
        String name,
        String shorthand,
        String description,
        String defaultValue,
        boolean required,
        boolean booleanFlag) {

    public static Flag string(String name, String description) {
        return new Flag(name, "", description, null, false, false);
    }

    public static Flag withDefault(String name, String description, String defaultValue) {
        return new Flag(name, "", description, defaultValue, false, false);
    }

    public static Flag bool(String name, String description) {
        return new Flag(name, "", description, "false", false, true);
    }

    public static Flag bool(String name, String description, boolean defaultValue) {
        return new Flag(name, "", description, Boolean.toString(defaultValue), false, true);
    }

    public static Flag string(String name, String shorthand, String description) {
        return new Flag(name, shorthand, description, null, false, false);
    }

    public static Flag bool(String name, String shorthand, String description, boolean defaultValue) {
        return new Flag(name, shorthand, description, Boolean.toString(defaultValue), false, true);
    }

    public static Flag required(String name, String description) {
        return new Flag(name, "", description, null, true, false);
    }
}