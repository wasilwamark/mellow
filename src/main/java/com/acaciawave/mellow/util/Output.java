package com.acaciawave.mellow.util;

/** Console output helpers; keeps the emoji prefixes from the Go implementation. */
public final class Output {

    private Output() {
    }

    public static void info(String message) {
        System.out.println("ℹ️  " + message);
    }

    public static void success(String message) {
        System.out.println("✅ " + message);
    }

    public static void warn(String message) {
        System.out.println("⚠️  " + message);
    }

    public static void error(String message) {
        System.err.println("❌ " + message);
    }

    public static void step(String message) {
        System.out.println("⚡ " + message);
    }

    public static void plain(String message) {
        System.out.println(message);
    }
}