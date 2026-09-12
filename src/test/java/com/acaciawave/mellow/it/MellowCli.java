package com.acaciawave.mellow.it;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.acaciawave.mellow.Providers;
import com.acaciawave.mellow.cli.Cli;
import com.acaciawave.mellow.config.MellowConfig;

/**
 * Runs the real Mellow CLI in-process and captures its exit code and output, so
 * integration tests assert exactly what a user would see.
 */
public final class MellowCli {

    static {
        // Capture runInteractive output instead of wiring process stdin/stdout,
        // which would corrupt the test harness channel.
        System.setProperty("mellow.ssh.captureInteractive", "true");
    }

    private final MellowConfig config;

    public MellowCli(Path homeDir) {
        this.config = new MellowConfig(homeDir);
    }

    /**
     * Registers a server alias plus its password, mirroring
     * {@code mellow alias add <alias> user@host:port --password ...}. The CLI
     * resolves SSH/sudo credentials from the alias secret store.
     */
    public void addServer(String alias, String userHostPort, String password) {
        config.setAlias(alias, userHostPort);
        if (password != null) {
            config.setSecret(alias, password);
        }
    }

    public Result run(String... argv) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            exitCode = new Cli(Providers.registry(config), config).run(argv, null);
        } catch (Throwable t) {
            exitCode = 1;
            t.printStackTrace(new PrintStream(err, true, StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new Result(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    public record Result(int exitCode, String stdout, String stderr) {

        public boolean success() {
            return exitCode == 0;
        }

        public String output() {
            return stdout + stderr;
        }

        /** True when an uncaught exception escaped (a real bug in the CLI). */
        public boolean crashed() {
            String all = output();
            return all.contains("\tat ")
                    || all.contains("Exception in thread")
                    || all.contains("NullPointerException")
                    || all.contains("ClassCastException")
                    || all.contains("UnsupportedOperationException");
        }
    }
}