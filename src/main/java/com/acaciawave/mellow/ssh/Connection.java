package com.acaciawave.mellow.ssh;

import com.acaciawave.mellow.distro.DistroInfo;

/**
 * Remote connection contract (port of {@code api.Connection}). The Java port
 * implements this with Apache MINA SSHD instead of shelling out to {@code ssh}.
 */
public interface Connection extends AutoCloseable {

    // --- command execution -------------------------------------------------

    CommandResult runCommand(String command, boolean sudo);

    default CommandResult runCommand(String command) {
        return runCommand(command, false);
    }

    CommandResult runSudo(String command, String password);

    String runCommandWithOutput(String command, boolean sudo);

    void runInteractive(String command);

    void shell();

    // --- file operations ---------------------------------------------------

    void writeFile(String content, String remotePath);

    void writeFileFromLocal(String localPath, String remotePath);

    void appendFile(String content, String remotePath);

    void copyFile(String source, String destination);

    void moveFile(String source, String destination);

    void deleteFile(String path);

    void createDirectory(String path);

    void removeDirectory(String path, boolean recursive);

    CommandResult listDirectory(String path);

    void uploadFile(String localPath, String remotePath);

    void downloadFile(String remotePath, String localPath);

    // --- probes ------------------------------------------------------------

    boolean fileExists(String path);

    boolean directoryExists(String path);

    boolean systemctl(String action, String service);

    boolean installPackage(String packageName);

    DistroInfo distroInfo();

    // --- info --------------------------------------------------------------

    String user();

    String host();

    int port();

    boolean connect();

    boolean isHealthy();

    void disconnect();

    @Override
    void close();
}