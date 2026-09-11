package com.acaciawave.mellow.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.acaciawave.mellow.MellowException;

/**
 * Minimal flag parser matching the Go CLI's ad-hoc parsing:
 * {@code --name value}, {@code --name=value}, {@code -x value} and boolean flags.
 */
public final class FlagParser {

    private FlagParser() {
    }

    public record Parsed(List<String> positional, Map<String, Object> flags) {
    }

    public static Parsed parse(Command command, List<String> rawArgs) {
        Map<String, Object> flags = new LinkedHashMap<>();
        Map<String, Flag> byName = new LinkedHashMap<>();
        Map<String, Flag> byShorthand = new LinkedHashMap<>();
        for (Flag flag : command.flags()) {
            byName.put(flag.name(), flag);
            if (!flag.shorthand().isEmpty()) {
                byShorthand.put(flag.shorthand(), flag);
            }
            if (flag.defaultValue() != null) {
                flags.put(flag.name(), flag.booleanFlag() ? Boolean.parseBoolean(flag.defaultValue()) : flag.defaultValue());
            }
        }

        List<String> positional = new ArrayList<>();
        for (int i = 0; i < rawArgs.size(); i++) {
            String token = rawArgs.get(i);

            if (token.startsWith("--") && token.length() > 2) {
                String body = token.substring(2);
                String name;
                String inlineValue = null;
                int eq = body.indexOf('=');
                if (eq >= 0) {
                    name = body.substring(0, eq);
                    inlineValue = body.substring(eq + 1);
                } else {
                    name = body;
                }
                Flag flag = byName.get(name);
                if (flag == null) {
                    throw new MellowException("unknown flag '--" + name + "' for command '" + command.name() + "'");
                }
                if (flag.booleanFlag()) {
                    flags.put(name, inlineValue == null || Boolean.parseBoolean(inlineValue));
                } else if (inlineValue != null) {
                    flags.put(name, inlineValue);
                } else if (i + 1 < rawArgs.size()) {
                    flags.put(name, rawArgs.get(++i));
                } else {
                    throw new MellowException("flag '--" + name + "' requires a value");
                }
                continue;
            }

            if (token.startsWith("-") && token.length() == 2 && !Character.isDigit(token.charAt(1))) {
                Flag flag = byShorthand.get(token.substring(1));
                if (flag == null) {
                    throw new MellowException("unknown flag '" + token + "' for command '" + command.name() + "'");
                }
                if (flag.booleanFlag()) {
                    flags.put(flag.name(), Boolean.TRUE);
                } else if (i + 1 < rawArgs.size()) {
                    flags.put(flag.name(), rawArgs.get(++i));
                } else {
                    throw new MellowException("flag '" + token + "' requires a value");
                }
                continue;
            }

            positional.add(token);
        }

        for (Flag flag : command.flags()) {
            if (flag.required() && !flags.containsKey(flag.name())) {
                throw new MellowException("missing required flag '--" + flag.name() + "'");
            }
        }
        return new Parsed(positional, flags);
    }
}