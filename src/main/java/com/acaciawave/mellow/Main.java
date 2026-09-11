package com.acaciawave.mellow;

import java.io.IOException;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.acaciawave.mellow.cli.Cli;
import com.acaciawave.mellow.cli.Registry;
import com.acaciawave.mellow.config.MellowConfig;

/** Native-image / JVM entrypoint. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        MellowConfig config = new MellowConfig();
        Registry registry = Providers.registry(config);

        Terminal terminal = openTerminal();
        try {
            int exitCode = new Cli(registry, config).run(args, terminal);
            System.exit(exitCode);
        } finally {
            close(terminal);
        }
    }

    /**
     * Builds a JLine terminal only when attached to a real console. When stdin
     * is piped (scripts, CI) the CLI runs without a terminal and commands that
     * do not prompt work unchanged.
     */
    private static Terminal openTerminal() {
        if (System.console() == null) {
            return null;
        }
        try {
            return TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            return null;
        }
    }

    private static void close(Terminal terminal) {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}