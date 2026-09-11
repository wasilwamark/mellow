package com.acaciawave.mellow.cli;

import java.util.List;

/**
 * A command provider — the Java replacement for the Go plugin system.
 * Services ({@code nginx}, {@code system}, …) and core commands
 * ({@code alias}) each implement this and are registered with the
 * {@link Registry} at startup.
 */
public interface Provider {

    /** Stable, lowercase provider name used on the command line. */
    String name();

    /** One-line human description. */
    String description();

    /** Commands exposed by this provider. */
    List<Command> commands();
}