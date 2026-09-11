package com.acaciawave.mellow.cli;

/** A single unit of work exposed by a {@link Provider}. */
@FunctionalInterface
public interface CommandHandler {
    void run(CommandContext context);
}