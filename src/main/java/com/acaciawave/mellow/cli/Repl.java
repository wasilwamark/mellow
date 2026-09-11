package com.acaciawave.mellow.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import com.acaciawave.mellow.config.MellowConfig;
import com.acaciawave.mellow.util.Output;

/**
 * Interactive Mellow shell (Phase 4). Reads commands with JLine, offering tab
 * completion for aliases/services/commands and persistent history.
 */
public final class Repl {

    private final Registry registry;
    private final MellowConfig config;
    private final Cli cli;

    public Repl(Registry registry, MellowConfig config, Cli cli) {
        this.registry = registry;
        this.config = config;
        this.cli = cli;
    }

    public void start(Terminal terminal) {
        ensureConfigDir();

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new MellowCompleter(registry, config))
                .variable(LineReader.HISTORY_FILE, config.configDir().resolve("history").toString())
                .option(LineReader.Option.CASE_INSENSITIVE, true)
                .build();

        printWelcome();

        while (true) {
            String line;
            try {
                line = reader.readLine("mellow> ");
            } catch (UserInterruptException e) {
                continue; // Ctrl-C clears the current line
            } catch (EndOfFileException e) {
                break; // Ctrl-D exits
            }

            if (line == null) {
                break;
            }
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("exit") || line.equals("quit")) {
                break;
            }

            String[] argv = tokenize(line);
            try {
                cli.run(argv, terminal);
            } catch (RuntimeException e) {
                Output.error(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }

        Output.plain("Bye.");
    }

    private void ensureConfigDir() {
        try {
            Files.createDirectories(config.configDir());
        } catch (IOException ignored) {
            // History simply will not persist if the directory is unavailable.
        }
    }

    private void printWelcome() {
        Output.plain("Mellow " + Cli.VERSION + " — interactive shell");
        Output.plain("Type a command (e.g. myserver system update), 'help', or 'exit'.");
        Output.plain("Press TAB to complete; services: "
                + String.join(", ", registry.all().stream().map(Provider::name).toList()));
    }

    /**
     * Splits a line into arguments, honouring single and double quotes so that
     * passwords and domains with spaces can be entered.
     */
    static String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean started = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                started = true;
            } else if (Character.isWhitespace(c)) {
                if (started) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
            } else {
                current.append(c);
                started = true;
            }
        }
        if (started) {
            tokens.add(current.toString());
        }
        return tokens.toArray(String[]::new);
    }
}