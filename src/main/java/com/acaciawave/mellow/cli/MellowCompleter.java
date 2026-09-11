package com.acaciawave.mellow.cli;

import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import com.acaciawave.mellow.config.MellowConfig;

/**
 * Tab completion for the interactive shell. Completes, in order:
 * <ol>
 *   <li>aliases, root commands and services (first word),</li>
 *   <li>services after a target, and</li>
 *   <li>commands for the selected service.</li>
 * </ol>
 */
public final class MellowCompleter implements Completer {

    private final Registry registry;
    private final MellowConfig config;

    public MellowCompleter(Registry registry, MellowConfig config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        List<String> words = line.words();
        int index = line.wordIndex();

        if (index == 0) {
            for (String alias : config.aliases().keySet()) {
                add(candidates, alias, "server alias");
            }
            add(candidates, "alias", "server alias management");
            add(candidates, "help", "show help");
            add(candidates, "version", "show version");
            add(candidates, "exit", "leave the shell");
            for (Provider provider : registry.all()) {
                add(candidates, provider.name(), provider.description());
            }
            return;
        }

        String first = words.get(0);

        if (first.equals("alias")) {
            if (index == 1) {
                add(candidates, "add", "add a server alias");
                add(candidates, "list", "list server aliases");
                add(candidates, "remove", "remove a server alias");
            }
            return;
        }

        // first is a target (alias or user@host)
        if (index == 1) {
            for (Provider provider : registry.all()) {
                add(candidates, provider.name(), provider.description());
            }
            return;
        }

        if (index == 2) {
            registry.get(words.get(1)).ifPresent(provider -> {
                for (Command command : provider.commands()) {
                    add(candidates, command.name(), command.description());
                }
            });
        }
    }

    private static void add(List<Candidate> candidates, String value, String description) {
        candidates.add(new Candidate(value, value, null, description, null, null, true));
    }
}