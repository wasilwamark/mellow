package com.acaciawave.mellow.ssh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.security.SecurityUtils;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.distro.DistroInfo;
import com.acaciawave.mellow.distro.OsRelease;
import com.acaciawave.mellow.pkgmgr.PackageManager;
import com.acaciawave.mellow.pkgmgr.PackageManagers;

/**
 * Apache MINA SSHD implementation of {@link Connection}. Replaces the Go code
 * that shelled out to the system {@code ssh} binary.
 */
public final class SshConnection implements Connection {

    /**
     * How long to wait for a remote command to finish. Deliberately generous:
     * package installs, image pulls and backups routinely take minutes.
     */
    private static final Duration COMMAND_TIMEOUT = Duration.ofHours(2);

    private final SshConfig config;

    private SshClient client;
    private ClientSession session;
    private DistroInfo distroInfo;

    public SshConnection(SshConfig config) {
        this.config = config;
    }

    // --- lifecycle ---------------------------------------------------------

    @Override
    public boolean connect() {
        if (session != null && session.isOpen()) {
            return true;
        }
        SshClient newClient = SshClient.setUpDefaultClient();
        // Parity with StrictHostKeyChecking=no
        newClient.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        newClient.start();
        try {
            ClientSession newSession = newClient
                    .connect(config.user(), config.host(), config.port())
                    .verify(config.timeout())
                    .getSession();

            if (config.password() != null && !config.password().isEmpty()) {
                newSession.addPasswordIdentity(config.password());
            }
            loadIdentityFiles(newSession);
            newSession.auth().verify(config.timeout());

            this.client = newClient;
            this.session = newSession;
            return true;
        } catch (Exception e) {
            if (Boolean.getBoolean("mellow.ssh.debug")) {
                System.err.println("SSH connection to " + config.user() + "@" + config.host() + ":"
                        + config.port() + " failed: " + e);
                e.printStackTrace();
            }
            newClient.stop();
            return false;
        }
    }

    private void loadIdentityFiles(ClientSession target) {
        for (Path keyFile : identityCandidates()) {
            if (!Files.isRegularFile(keyFile)) {
                continue;
            }
            try {
                Iterable<KeyPair> keyPairs = SecurityUtils.getKeyPairResourceParser()
                        .loadKeyPairs(null, keyFile, null);
                for (KeyPair keyPair : keyPairs) {
                    target.addPublicKeyIdentity(keyPair);
                }
            } catch (Exception ignored) {
                // Not a readable/parseable key — skip and try the next one.
            }
        }
    }

    private List<Path> identityCandidates() {
        String home = System.getProperty("user.home", ".");
        List<Path> candidates = new ArrayList<>();
        if (config.identityFile() != null && !config.identityFile().isBlank()) {
            candidates.add(expandHome(config.identityFile(), home));
            return candidates;
        }
        candidates.add(Path.of(home, ".ssh", "id_ed25519"));
        candidates.add(Path.of(home, ".ssh", "id_ecdsa"));
        candidates.add(Path.of(home, ".ssh", "id_rsa"));
        return candidates;
    }

    private static Path expandHome(String path, String home) {
        if (path.startsWith("~/")) {
            return Path.of(home, path.substring(2));
        }
        return Path.of(path);
    }

    @Override
    public boolean isHealthy() {
        return session != null && session.isOpen() && session.isAuthenticated();
    }

    @Override
    public void disconnect() {
        close();
    }

    @Override
    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (IOException ignored) {
                // best effort
            }
            session = null;
        }
        if (client != null) {
            client.stop();
            client = null;
        }
    }

    private ClientSession requireSession() {
        if (session == null || !session.isOpen()) {
            throw new MellowException("not connected to " + config.user() + "@" + config.host());
        }
        return session;
    }

    // --- command execution -------------------------------------------------

    @Override
    public CommandResult runCommand(String command, boolean sudo) {
        return exec(sudo ? "sudo -S " + command : command, null);
    }

    @Override
    public CommandResult runSudo(String command, String password) {
        if (password == null || password.isEmpty()) {
            return CommandResult.failure("sudo password is required for sudo commands");
        }
        return exec("echo " + shellQuote(password) + " | sudo -S " + command, null);
    }

    @Override
    public String runCommandWithOutput(String command, boolean sudo) {
        CommandResult result = runCommand(command, sudo);
        if (!result.success()) {
            throw new MellowException("command failed: " + result.stderr());
        }
        return result.stdout();
    }

    @Override
    public void runInteractive(String command) {
        // In headless/test contexts (mellow.ssh.captureInteractive=true) do not wire
        // the process streams to the pty: streaming stdin/stdout would fight with the
        // test harness and pty pagers (e.g. `systemctl status`) can block on stdin.
        if (Boolean.getBoolean("mellow.ssh.captureInteractive")) {
            exec(command, null);
            return;
        }
        ClientSession target = requireSession();
        try (ChannelExec channel = target.createExecChannel(command)) {
            channel.setPtyType("xterm-256color");
            channel.setIn(System.in);
            channel.setOut(System.out);
            channel.setErr(System.err);
            channel.open().verify(config.timeout());
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
        } catch (IOException e) {
            throw new MellowException("interactive command failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void shell() {
        ClientSession target = requireSession();
        try (ChannelShell channel = target.createShellChannel()) {
            channel.setIn(System.in);
            channel.setOut(System.out);
            channel.setErr(System.err);
            channel.open().verify(config.timeout());
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0L);
        } catch (IOException e) {
            throw new MellowException("shell failed: " + e.getMessage(), e);
        }
    }

    private CommandResult exec(String command, byte[] stdin) {
        ClientSession target = requireSession();
        Instant start = Instant.now();
        try (ClientChannel channel = target.createExecChannel(command)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setOut(out);
            channel.setErr(err);
            if (stdin != null) {
                channel.setIn(new ByteArrayInputStream(stdin));
            }
            channel.open().verify(config.timeout());
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), COMMAND_TIMEOUT.toMillis());
            int exitCode = channel.getExitStatus() == null ? -1 : channel.getExitStatus();
            return new CommandResult(
                    exitCode == 0,
                    out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8),
                    exitCode,
                    Duration.between(start, Instant.now()));
        } catch (IOException e) {
            return new CommandResult(false, "", e.getMessage(), -1, Duration.between(start, Instant.now()));
        }
    }

    // --- file operations ---------------------------------------------------

    @Override
    public void writeFile(String content, String remotePath) {
        CommandResult result = exec("base64 -d > " + shellQuote(remotePath), content.getBytes(StandardCharsets.UTF_8));
        if (!result.success()) {
            throw new MellowException("failed to write file: " + result.stderr());
        }
    }

    @Override
    public void writeFileFromLocal(String localPath, String remotePath) {
        try {
            writeFile(Files.readString(Path.of(localPath)), remotePath);
        } catch (IOException e) {
            throw new MellowException("failed to read local file: " + e.getMessage(), e);
        }
    }

    @Override
    public void uploadFile(String localPath, String remotePath) {
        writeFileFromLocal(localPath, remotePath);
    }

    @Override
    public void appendFile(String content, String remotePath) {
        CommandResult result = exec("base64 -d >> " + shellQuote(remotePath), content.getBytes(StandardCharsets.UTF_8));
        if (!result.success()) {
            throw new MellowException("failed to append file: " + result.stderr());
        }
    }

    @Override
    public void downloadFile(String remotePath, String localPath) {
        CommandResult result = runCommand("cat " + shellQuote(remotePath), false);
        if (!result.success()) {
            throw new MellowException("failed to read remote file: " + result.stderr());
        }
        try {
            Files.writeString(Path.of(localPath), result.stdout());
        } catch (IOException e) {
            throw new MellowException("failed to write local file: " + e.getMessage(), e);
        }
    }

    @Override
    public void copyFile(String source, String destination) {
        runSimple("cp " + shellQuote(source) + " " + shellQuote(destination), "copy file");
    }

    @Override
    public void moveFile(String source, String destination) {
        runSimple("mv " + shellQuote(source) + " " + shellQuote(destination), "move file");
    }

    @Override
    public void deleteFile(String path) {
        runSimple("rm -f " + shellQuote(path), "delete file");
    }

    @Override
    public void createDirectory(String path) {
        runSimple("mkdir -p " + shellQuote(path), "create directory");
    }

    @Override
    public void removeDirectory(String path, boolean recursive) {
        runSimple((recursive ? "rm -rf " : "rmdir ") + shellQuote(path), "remove directory");
    }

    @Override
    public CommandResult listDirectory(String path) {
        return runCommand("ls -la " + shellQuote(path), false);
    }

    private void runSimple(String command, String action) {
        CommandResult result = runCommand(command, false);
        if (!result.success()) {
            throw new MellowException("failed to " + action + ": " + result.stderr());
        }
    }

    // --- probes ------------------------------------------------------------

    @Override
    public boolean fileExists(String path) {
        return runCommand("test -f " + shellQuote(path), false).success();
    }

    @Override
    public boolean directoryExists(String path) {
        return runCommand("test -d " + shellQuote(path), false).success();
    }

    @Override
    public boolean systemctl(String action, String service) {
        return runCommand("systemctl " + action + " " + service, true).success();
    }

    @Override
    public boolean installPackage(String packageName) {
        PackageManager pkg = PackageManagers.forDistro(distroInfo());
        return runCommand(pkg.install(packageName), true).success();
    }

    @Override
    public DistroInfo distroInfo() {
        if (distroInfo == null) {
            CommandResult result = runCommand("cat /etc/os-release", false);
            distroInfo = result.success() ? OsRelease.detect(result.stdout()) : DistroInfo.unknown();
        }
        return distroInfo;
    }

    // --- info --------------------------------------------------------------

    @Override
    public String user() {
        return config.user();
    }

    @Override
    public String host() {
        return config.host();
    }

    @Override
    public int port() {
        return config.port();
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** Exposed for tests / connectors. */
    public SshConfig config() {
        return config;
    }

    @SuppressWarnings("unused")
    private static InputStream emptyInput() {
        return new ByteArrayInputStream(new byte[0]);
    }
}