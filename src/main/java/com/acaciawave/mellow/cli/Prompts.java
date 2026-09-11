package com.acaciawave.mellow.cli;

import java.util.Optional;
import java.util.Scanner;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

/**
 * Interactive prompts. Uses JLine when a terminal is attached (masked/edited
 * input) and falls back to plain {@code System.in} otherwise.
 */
public final class Prompts {

    private Prompts() {
    }

    /**
     * Reads an integer in {@code [min, max]}. Returns empty on EOF, invalid
     * input or out-of-range values (callers treat empty as "cancel").
     */
    public static Optional<Integer> readInt(Terminal terminal, String prompt, int min, int max) {
        String raw = readLine(terminal, prompt);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value < min || value > max) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Reads a line, returning null on EOF. */
    public static String readLine(Terminal terminal, String prompt) {
        if (terminal != null) {
            try {
                LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
                return reader.readLine(prompt);
            } catch (Exception e) {
                return null;
            }
        }
        System.out.print(prompt);
        Scanner scanner = new Scanner(System.in);
        return scanner.hasNextLine() ? scanner.nextLine() : null;
    }
}