package com.acaciawave.mellow.cli;

/** Type of a command argument (mirrors {@code api.ArgumentType} in the Go tree). */
public enum ArgumentType {
    STRING,
    INT,
    BOOL,
    SLICE
}