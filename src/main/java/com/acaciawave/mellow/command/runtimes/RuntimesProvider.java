package com.acaciawave.mellow.command.runtimes;

import java.util.List;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.ssh.Connection;
import com.acaciawave.mellow.util.Output;

/**
 * Language runtime provider (port of {@code internal/services/runtimes}).
 *
 * <p>User-space installers (nvm, uv, rbenv, rustup) run as the SSH user;
 * package-manager operations run via sudo.</p>
 */
public final class RuntimesProvider implements Provider {

    @Override
    public String name() {
        return "runtime";
    }

    @Override
    public String description() {
        return "Manage programming language runtime (Node.js, Python, Go, Java, etc.)";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.builder("install", "Install a language runtime", this::install)
                        .arg(Argument.required("language", "node, python, go, java, rust, php, ruby, dotnet"))
                        .arg(Argument.required("version", "Version to install"))
                        .build(),
                Command.of("list", "List available and installed runtime", this::list),
                Command.builder("use", "Switch to a specific version of a runtime", this::use)
                        .arg(Argument.required("language", "Language"))
                        .arg(Argument.required("version", "Version"))
                        .build(),
                Command.builder("remove", "Remove a runtime version", this::remove)
                        .arg(Argument.required("language", "Language"))
                        .arg(Argument.required("version", "Version"))
                        .build(),
                Command.of("status", "Show current active runtime", this::status),
                Command.of("update", "Update runtime version managers", this::update));
    }

    // --- install -----------------------------------------------------------

    private void install(CommandContext context) {
        if (context.argCount() < 2) {
            throw new MellowException("usage: runtime install <language> <version> [options]\n\n"
                    + "Example: runtime install node 18\n"
                    + "Example: runtime install python 3.11\n"
                    + "Example: runtime install go 1.21");
        }
        String language = context.arg(0).toLowerCase();
        String version = context.arg(1);
        String password = ServiceSupport.password(context);

        switch (language) {
            case "node", "nodejs", "node.js" -> installNode(context.connection(), version);
            case "python", "py", "python3" -> installPython(context.connection(), version, password);
            case "go", "golang" -> installGo(context.connection(), version, password);
            case "java", "jdk" -> installJava(context.connection(), version, password);
            case "rust" -> installRust(context.connection());
            case "php" -> installPhp(context.connection(), version, password);
            case "ruby" -> installRuby(context.connection(), version, password);
            case "dotnet", ".net" -> installDotnet(context.connection(), version, password);
            default -> throw new MellowException("unsupported language: " + language
                    + ". Supported languages: node, python, go, java, rust, php, ruby, dotnet");
        }
    }

    private void installNode(Connection connection, String version) {
        Output.plain("📦 Installing Node.js " + version + "...");

        if (!connection.runCommand("command -v nvm", false).success()) {
            Output.plain("🔧 Installing NVM (Node Version Manager)...");
            if (!connection.runCommand(
                    "curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash", false).success()) {
                throw new MellowException("failed to install NVM");
            }
            String profile = "echo 'export NVM_DIR=\"$HOME/.nvm\"' >> ~/.bashrc && "
                    + "echo '[ -s \"$NVM_DIR/nvm.sh\" ] && \\. \"$NVM_DIR/nvm.sh\"' >> ~/.bashrc && "
                    + "echo '[ -s \"$NVM_DIR/bash_completion\" ] && \\. \"$NVM_DIR/bash_completion\"' >> ~/.bashrc";
            CommandResult profileResult = connection.runCommand(profile, false);
            if (!profileResult.success()) {
                Output.warn("Failed to update bashrc: " + profileResult.stderr());
            }
        }

        String install = "bash -c 'source ~/.nvm/nvm.sh && nvm install " + version + " && nvm use " + version
                + " && nvm alias default " + version + "'";
        CommandResult result = connection.runCommand(install, false);
        if (!result.success()) {
            throw new MellowException("failed to install Node.js " + version + ": " + result.stderr());
        }

        CommandResult verify = connection.runCommand(
                "bash -c 'source ~/.nvm/nvm.sh && node --version && npm --version'", false);
        if (!verify.success()) {
            Output.warn("Failed to verify Node.js installation: " + verify.stderr());
        } else {
            Output.success("Node.js " + version + " installed successfully!");
            Output.plain(verify.stdout().strip());
        }
    }

    private void installPython(Connection connection, String version, String password) {
        Output.plain("📦 Installing Python " + version + "...");

        if (!connection.runCommand("command -v uv", false).success()) {
            Output.plain("🔧 Installing uv...");
            CommandResult deps = connection.runSudo(
                    "apt-get update 2>/dev/null || true && apt-get install -y curl", password);
            if (!deps.success()) {
                throw new MellowException("failed to install dependencies for uv: " + deps.stderr());
            }
            if (!connection.runCommand("curl -LsSf https://astral.sh/uv/install.sh | sh", false).success()) {
                throw new MellowException("failed to install uv");
            }
            CommandResult profile = connection.runCommand(
                    "echo 'export PATH=\"$HOME/.local/bin:$PATH\"' >> ~/.bashrc", false);
            if (!profile.success()) {
                Output.warn("Failed to update bashrc: " + profile.stderr());
            }
        }

        String install = "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv python install " + version + "'";
        CommandResult result = connection.runCommand(install, false);
        if (!result.success()) {
            throw new MellowException("failed to install Python " + version + ": " + result.stderr());
        }

        CommandResult pin = connection.runCommand(
                "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv python pin " + version + "'", false);
        if (!pin.success()) {
            Output.warn("Failed to pin Python " + version + ": " + pin.stderr());
        }

        CommandResult verify = connection.runCommand(
                "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv run python --version && uv run pip --version'",
                false);
        if (!verify.success()) {
            Output.warn("Failed to verify Python installation: " + verify.stderr());
        } else {
            Output.success("Python " + version + " installed successfully!");
            Output.plain(verify.stdout().strip());
        }
    }

    private void installGo(Connection connection, String version, String password) {
        Output.plain("📦 Installing Go " + version + "...");
        String goVersion = normalizeGoVersion(version);

        String download = "wget https://go.dev/dl/go" + goVersion + ".linux-amd64.tar.gz -O /tmp/go"
                + goVersion + ".linux-amd64.tar.gz";
        if (!connection.runCommand(download, false).success()) {
            throw new MellowException("failed to download Go");
        }

        CommandResult extract = connection.runSudo(
                "tar -C /usr/local -xzf /tmp/go" + goVersion + ".linux-amd64.tar.gz", password);
        if (!extract.success()) {
            throw new MellowException("failed to extract Go: " + extract.stderr());
        }
        connection.runCommand("rm /tmp/go" + goVersion + ".linux-amd64.tar.gz", false);

        String profile = "echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc && "
                + "echo 'export GOPATH=$HOME/go' >> ~/.bashrc && "
                + "echo 'export PATH=$PATH:$GOPATH/bin' >> ~/.bashrc";
        CommandResult profileResult = connection.runCommand(profile, false);
        if (!profileResult.success()) {
            Output.warn("Failed to update bashrc: " + profileResult.stderr());
        }

        CommandResult verify = connection.runCommand("/usr/local/go/bin/go version", false);
        if (!verify.success()) {
            Output.warn("Failed to verify Go installation: " + verify.stderr());
        } else {
            Output.success("Go " + version + " installed successfully!");
            Output.plain(verify.stdout().strip());
            Output.plain("📝 Note: Run 'source ~/.bashrc' or logout/login to update PATH for go command");
        }
    }

    private void installJava(Connection connection, String version, String password) {
        Output.plain("📦 Installing Java " + version + "...");
        String install = javaInstallCommand(version);

        CommandResult result = connection.runSudo(install, password);
        if (!result.success()) {
            throw new MellowException("failed to install Java " + version + ": " + result.stderr());
        }

        String homeCmd = "echo 'export JAVA_HOME=/usr/lib/jvm/java-'$(ls /usr/lib/jvm | grep openjdk | head -n 1 "
                + "| cut -d'-' -f2)'-openjdk-amd64' >> ~/.bashrc";
        CommandResult home = connection.runCommand(homeCmd, false);
        if (!home.success()) {
            Output.warn("Failed to set JAVA_HOME: " + home.stderr());
        }

        CommandResult verify = connection.runCommand(
                "bash -c 'source ~/.bashrc && java -version && javac -version'", false);
        if (!verify.success()) {
            Output.warn("Failed to verify Java installation: " + verify.stderr());
        } else {
            Output.success("Java " + version + " installed successfully!");
            Output.plain(verify.stdout().strip());
        }
    }

    private void installRust(Connection connection) {
        Output.plain("📦 Installing Rust...");

        CommandResult result = connection.runCommand(
                "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y", false);
        if (!result.success()) {
            throw new MellowException("failed to install Rust: " + result.stderr());
        }

        CommandResult profile = connection.runCommand(
                "echo 'export PATH=\"$HOME/.cargo/bin:$PATH\"' >> ~/.bashrc", false);
        if (!profile.success()) {
            Output.warn("Failed to update bashrc: " + profile.stderr());
        }

        CommandResult verify = connection.runCommand(
                "bash -c 'export PATH=\"$HOME/.cargo/bin:$PATH\" && rustc --version && cargo --version'", false);
        if (!verify.success()) {
            Output.warn("Failed to verify Rust installation: " + verify.stderr());
        } else {
            Output.success("Rust installed successfully!");
            Output.plain(verify.stdout().strip());
        }
    }

    private void installPhp(Connection connection, String version, String password) {
        Output.plain("📦 Installing PHP " + version + "...");

        if (version.compareTo("8.0") > 0) {
            CommandResult ppa = connection.runSudo(
                    "apt-get install -y software-properties-common && "
                            + "add-apt-repository -y ppa:ondrej/php 2>/dev/null || true", password);
            if (!ppa.success()) {
                Output.warn("Failed to add PHP PPA, trying with default repositories");
            }
        }

        CommandResult result = connection.runSudo(phpInstallCommand(version), password);
        if (!result.success()) {
            Output.warn("PHP " + version + " not available, trying with default PHP version...");
            result = connection.runSudo("apt-get update 2>/dev/null || true && "
                    + "apt-get install -y php php-cli php-fpm php-mbstring php-xml php-curl", password);
            if (!result.success()) {
                throw new MellowException("failed to install PHP: " + result.stderr());
            }
        }

        CommandResult verify = connection.runCommand("php" + version + " --version", false);
        if (!verify.success()) {
            verify = connection.runCommand("php --version", false);
        }
        if (!verify.success()) {
            Output.warn("Failed to verify PHP installation: " + verify.stderr());
        } else {
            Output.success("PHP " + version + " installed successfully!");
            Output.plain(verify.stdout().strip());
        }
    }

    private void installRuby(Connection connection, String version, String password) {
        Output.plain("📦 Installing Ruby " + version + "...");
        String rubyVersion = normalizeRubyVersion(version);

        CommandResult deps = connection.runSudo("apt-get update 2>/dev/null || true && "
                + "apt-get install -y autoconf bison build-essential libssl-dev libyaml-dev "
                + "libreadline6-dev zlib1g-dev libncurses5-dev libffi-dev libgdbm-dev git", password);
        if (!deps.success()) {
            throw new MellowException("failed to install Ruby build dependencies: " + deps.stderr());
        }

        if (!connection.runCommand("test -d ~/.rbenv", false).success()) {
            CommandResult rbenv = connection.runCommand(
                    "git clone https://github.com/rbenv/rbenv.git ~/.rbenv && "
                            + "git clone https://github.com/rbenv/ruby-build.git ~/.rbenv/plugins/ruby-build", false);
            if (!rbenv.success()) {
                throw new MellowException("failed to install rbenv: " + rbenv.stderr());
            }
        }

        CommandResult profile = connection.runCommand(
                "echo 'export PATH=\"$HOME/.rbenv/bin:$PATH\"' >> ~/.bashrc && "
                        + "echo 'eval \"$(rbenv init -)\"' >> ~/.bashrc", false);
        if (!profile.success()) {
            Output.warn("Failed to update bashrc: " + profile.stderr());
        }

        String install = "bash -c 'export PATH=\"$HOME/.rbenv/bin:$PATH\" && eval \"$(rbenv init -)\" && "
                + "rbenv install " + rubyVersion + " --skip-existing && rbenv global " + rubyVersion + "'";
        CommandResult result = connection.runCommand(install, false);
        if (!result.success()) {
            throw new MellowException("failed to install Ruby " + version + ": " + result.stderr());
        }

        CommandResult verify = connection.runCommand(
                "bash -c 'export PATH=\"$HOME/.rbenv/bin:$PATH\" && eval \"$(rbenv init -)\" && "
                        + "ruby --version && gem --version'", false);
        if (!verify.success()) {
            Output.warn("Failed to verify Ruby installation: " + verify.stderr());
        } else {
            Output.success("Ruby " + rubyVersion + " installed successfully!");
            Output.plain(verify.stdout().strip());
        }
    }

    private void installDotnet(Connection connection, String version, String password) {
        Output.plain("📦 Installing .NET " + version + "...");

        String ubuntuVersion = "20.04";
        CommandResult release = connection.runCommand("lsb_release -rs | cut -d. -f1", false);
        if (release.success()) {
            String major = release.stdout().strip();
            if (major.equals("22") || major.equals("24")) {
                ubuntuVersion = major + ".04";
            }
        }

        String repo = "apt-get update 2>/dev/null || true && apt-get install -y wget && "
                + "wget https://packages.microsoft.com/config/ubuntu/" + ubuntuVersion
                + "/packages-microsoft-prod.deb -O packages-microsoft-prod.deb && "
                + "dpkg -i packages-microsoft-prod.deb";
        CommandResult repoResult = connection.runSudo(repo, password);
        if (!repoResult.success()) {
            throw new MellowException("failed to add Microsoft repository: " + repoResult.stderr());
        }

        CommandResult install = connection.runSudo("apt-get update 2>/dev/null || true && "
                + "apt-get install -y dotnet-sdk-" + normalizeDotnetVersion(version), password);
        if (!install.success()) {
            throw new MellowException("failed to install .NET " + version + ": " + install.stderr());
        }

        CommandResult verify = connection.runCommand("dotnet --version", false);
        if (!verify.success()) {
            Output.warn("Failed to verify .NET installation: " + verify.stderr());
        } else {
            Output.success(".NET " + version + " installed successfully!");
            Output.plain(verify.stdout().strip());
        }
    }

    // --- use / remove ------------------------------------------------------

    private void use(CommandContext context) {
        if (context.argCount() < 2) {
            throw new MellowException("usage: runtime use <language> <version>");
        }
        String language = context.arg(0).toLowerCase();
        String version = context.arg(1);
        switch (language) {
            case "node", "nodejs", "node.js" -> useNode(context.connection(), version);
            case "python", "py", "python3" -> usePython(context.connection(), version);
            case "go", "golang" -> Output.plain("Go version switching is not supported. Please reinstall the desired version.");
            case "java", "jdk" -> Output.plain("Java version switching requires update-alternatives. "
                    + "Please use: sudo update-alternatives --config java");
            default -> throw new MellowException("runtime switching not supported for " + language);
        }
    }

    private void useNode(Connection connection, String version) {
        Output.plain("🔄 Switching to Node.js " + version + "...");
        CommandResult result = connection.runCommand(
                "bash -c 'source ~/.nvm/nvm.sh && nvm use " + version + " && nvm alias default " + version + "'", false);
        if (!result.success()) {
            throw new MellowException("failed to switch Node.js version: " + result.stderr());
        }
        CommandResult verify = connection.runCommand(
                "bash -c 'source ~/.nvm/nvm.sh && node --version'", false);
        if (!verify.success()) {
            throw new MellowException("failed to verify Node.js version: " + verify.stderr());
        }
        Output.success("Now using Node.js " + verify.stdout().strip());
    }

    private void usePython(Connection connection, String version) {
        Output.plain("🔄 Switching to Python " + version + "...");
        CommandResult result = connection.runCommand(
                "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv python pin " + version + "'", false);
        if (!result.success()) {
            throw new MellowException("failed to switch Python version: " + result.stderr());
        }
        CommandResult verify = connection.runCommand(
                "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv run python --version'", false);
        if (!verify.success()) {
            throw new MellowException("failed to verify Python version: " + verify.stderr());
        }
        Output.success("Now using Python " + verify.stdout().strip());
    }

    private void remove(CommandContext context) {
        if (context.argCount() < 2) {
            throw new MellowException("usage: runtime remove <language> <version>");
        }
        String language = context.arg(0).toLowerCase();
        String version = context.arg(1);
        String password = ServiceSupport.password(context);
        Connection connection = context.connection();

        switch (language) {
            case "node", "nodejs", "node.js" -> {
                CommandResult result = connection.runCommand(
                        "bash -c 'source ~/.nvm/nvm.sh && nvm uninstall " + version + "'", false);
                requireSuccess(result, "failed to uninstall Node.js " + version);
                Output.success("Node.js " + version + " uninstalled");
            }
            case "python", "py", "python3" -> {
                CommandResult result = connection.runCommand(
                        "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv python uninstall " + version + "'", false);
                requireSuccess(result, "failed to uninstall Python " + version);
                Output.success("Python " + version + " uninstalled");
            }
            case "go", "golang" -> {
                CommandResult found = connection.runCommand(
                        "/usr/local/go/bin/go version | grep 'go" + version + "'", false);
                if (!found.success()) {
                    throw new MellowException("Go " + version + " not found in /usr/local/go");
                }
                requireSuccess(connection.runSudo("rm -rf /usr/local/go", password), "failed to remove Go " + version);
                connection.runCommand("sed -i '/export PATH=\\$PATH:\\/usr\\/local\\/go\\/bin/d' ~/.bashrc && "
                        + "sed -i '/export GOPATH=\\$HOME\\/go/d' ~/.bashrc && "
                        + "sed -i '/export PATH=\\$PATH:\\$GOPATH\\/bin/d' ~/.bashrc", false);
                Output.success("Go " + version + " uninstalled");
            }
            case "java", "jdk" -> {
                CommandResult found = connection.runCommand(
                        "ls /usr/lib/jvm/ | grep -E 'java-" + version + "-openjdk' | head -n 1", false);
                if (!found.success() || found.stdout().isBlank()) {
                    throw new MellowException("Java " + version + " not found in /usr/lib/jvm");
                }
                String javaDir = found.stdout().strip();
                requireSuccess(connection.runSudo("rm -rf /usr/lib/jvm/" + javaDir, password),
                        "failed to remove Java " + version);
                connection.runCommand("sed -i '/export JAVA_HOME/d' ~/.bashrc", false);
                Output.success("Java " + version + " uninstalled");
            }
            case "rust" -> {
                CommandResult result = connection.runCommand("bash -c 'source ~/.bashrc && rustup self uninstall -y'", false);
                if (!result.success()) {
                    requireSuccess(connection.runCommand("rm -rf ~/.cargo && rm -rf ~/.rustup", false),
                            "failed to remove Rust");
                }
                connection.runCommand("sed -i '/export PATH=\"\\$HOME\\/\\.cargo\\/bin:\\$PATH\"/d' ~/.bashrc", false);
                Output.success("Rust uninstalled");
            }
            case "php" -> {
                requireSuccess(connection.runSudo(phpRemoveCommand(version), password),
                        "failed to remove PHP " + version);
                Output.success("PHP " + version + " uninstalled");
            }
            case "ruby" -> {
                CommandResult result = connection.runCommand(
                        "bash -c 'export PATH=\"$HOME/.rbenv/bin:$PATH\" && eval \"$(rbenv init -)\" && "
                                + "rbenv uninstall " + version + "'", false);
                requireSuccess(result, "failed to uninstall Ruby " + version);
                Output.success("Ruby " + version + " uninstalled");
            }
            case "dotnet", ".net", "net" -> {
                requireSuccess(connection.runSudo("apt-get remove -y dotnet-sdk-" + version, password),
                        "failed to remove .NET " + version);
                Output.success(".NET " + version + " uninstalled");
            }
            default -> throw new MellowException("runtime removal not supported for " + language
                    + ". Supported languages: node, python, go, java, rust, php, ruby, dotnet");
        }
    }

    private static void requireSuccess(CommandResult result, String message) {
        if (!result.success()) {
            throw new MellowException(message + ": " + result.stderr());
        }
    }

    // --- list / status / update -------------------------------------------

    private void list(CommandContext context) {
        Output.plain("📋 Available Runtimes:");
        Output.plain("");

        record RuntimeCheck(String name, String command) {
        }
        List<RuntimeCheck> checks = List.of(
                new RuntimeCheck("Node.js", "bash -c 'source ~/.nvm/nvm.sh && nvm list'"),
                new RuntimeCheck("Python", "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv python list'"),
                new RuntimeCheck("Go", "go version"),
                new RuntimeCheck("Java", "java -version 2>&1 && javac -version 2>&1"),
                new RuntimeCheck("Rust", "rustc --version"),
                new RuntimeCheck("PHP", "php --version"),
                new RuntimeCheck("Ruby", "bash -c 'export PATH=\"$HOME/.rbenv/bin:$PATH\" && eval \"$(rbenv init -)\" && ruby --version'"),
                new RuntimeCheck(".NET", "dotnet --version"));

        for (RuntimeCheck check : checks) {
            Output.plain("=== " + check.name() + " ===");
            CommandResult result = context.connection().runCommand(check.command(), false);
            if (result.success() && !result.stdout().isBlank()) {
                for (String line : result.stdout().strip().split("\n")) {
                    Output.plain("  " + line);
                }
            } else {
                Output.plain("  Not installed or not in PATH");
            }
            Output.plain("");
        }
    }

    private void status(CommandContext context) {
        Output.plain("🔍 Current Runtime Status:");
        Output.plain("");

        record RuntimeCheck(String name, String command) {
        }
        List<RuntimeCheck> checks = List.of(
                new RuntimeCheck("Node.js", "bash -c 'source ~/.nvm/nvm.sh && echo \"Node: $(node --version 2>/dev/null || echo \"Not found\")\" && echo \"NPM: $(npm --version 2>/dev/null || echo \"Not found\")\"'"),
                new RuntimeCheck("Python", "bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && echo \"Python: $(uv run python --version 2>/dev/null || echo \"Not found\")\" && echo \"UV: $(uv --version 2>/dev/null || echo \"Not found\")\"'"),
                new RuntimeCheck("Go", "bash -c 'echo \"Go: $(/usr/local/go/bin/go version 2>/dev/null || echo \"Not found\")\"'"),
                new RuntimeCheck("Java", "bash -c 'echo \"Java: $(java -version 2>&1 | head -n 1 || echo \"Not found\")\"'"),
                new RuntimeCheck("Rust", "bash -c 'echo \"Rust: $(rustc --version 2>/dev/null || echo \"Not found\")\"'"),
                new RuntimeCheck("PHP", "bash -c 'echo \"PHP: $(php --version 2>/dev/null | head -n 1 || echo \"Not found\")\"'"),
                new RuntimeCheck("Ruby", "bash -c 'export PATH=\"$HOME/.rbenv/bin:$PATH\" && eval \"$(rbenv init -)\" && echo \"Ruby: $(ruby --version 2>/dev/null || echo \"Not found\")\"'"),
                new RuntimeCheck(".NET", "bash -c 'echo \".NET: $(dotnet --version 2>/dev/null || echo \"Not found\")\"'"));

        for (RuntimeCheck check : checks) {
            Output.plain("=== " + check.name() + " ===");
            CommandResult result = context.connection().runCommand(check.command(), false);
            if (result.success() && !result.stdout().isBlank()) {
                for (String line : result.stdout().strip().split("\n")) {
                    Output.plain("  " + line);
                }
            } else {
                Output.plain("  Not installed or not in PATH");
            }
            Output.plain("");
        }
    }

    private void update(CommandContext context) {
        Output.plain("🔄 Updating runtime version managers...");

        Output.plain("\n📦 Updating NVM...");
        if (!context.connection().runCommand("bash -c 'source ~/.nvm/nvm.sh && nvm update-version'", false).success()) {
            Output.plain("  NVM update failed or not installed");
        }
        Output.plain("\n📦 Updating uv...");
        if (!context.connection().runCommand("bash -c 'export PATH=\"$HOME/.local/bin:$PATH\" && uv self update'", false).success()) {
            Output.plain("  uv update failed or not installed");
        }
        Output.plain("\n📦 Updating rbenv...");
        if (!context.connection().runCommand("bash -c 'export PATH=\"$HOME/.rbenv/bin:$PATH\" && eval \"$(rbenv init -)\" && rbenv update'", false).success()) {
            Output.plain("  rbenv update failed or not installed");
        }
        Output.plain("\n📦 Updating Rust...");
        if (!context.connection().runCommand("bash -c 'rustup update'", false).success()) {
            Output.plain("  Rust update failed or not installed");
        }
        Output.plain("\n✅ Runtime managers updated!");
    }

    // --- pure helpers (unit-tested) ---------------------------------------

    static String normalizeGoVersion(String version) {
        if (!version.contains(".")) {
            return version + ".22.0";
        }
        if (version.split("\\.").length == 2) {
            return version + ".0";
        }
        return version;
    }

    static String normalizeRubyVersion(String version) {
        if (version.equals("3")) {
            return "3.3.0";
        }
        int parts = version.split("\\.").length;
        if (parts == 1) {
            return version + ".0.0";
        }
        if (parts == 2) {
            return version + ".0";
        }
        return version;
    }

    static String normalizeDotnetVersion(String version) {
        return version.split("\\.").length == 1 ? version + ".0" : version;
    }

    static String javaInstallCommand(String version) {
        if (version.startsWith("8")) {
            return "apt-get update 2>/dev/null || true && apt-get install -y openjdk-8-jdk";
        }
        if (version.startsWith("11")) {
            return "apt-get update 2>/dev/null || true && apt-get install -y openjdk-11-jdk";
        }
        if (version.startsWith("17")) {
            return "apt-get update 2>/dev/null || true && apt-get install -y openjdk-17-jdk";
        }
        if (version.startsWith("21")) {
            return "apt-get update 2>/dev/null || true && apt-get install -y openjdk-21-jdk";
        }
        throw new MellowException("unsupported Java version: " + version + ". Supported versions: 8, 11, 17, 21");
    }

    static String phpInstallCommand(String version) {
        String resolved = version.equals("8") ? "8.1" : version;
        String prefix = "apt-get update 2>/dev/null || true && apt-get install -y ";
        return prefix + "php" + resolved + " php" + resolved + "-cli php" + resolved + "-fpm php" + resolved
                + "-mbstring php" + resolved + "-xml php" + resolved + "-curl";
    }

    static String phpRemoveCommand(String version) {
        return "apt-get remove -y php" + version + " php" + version + "-cli php" + version + "-fpm php" + version
                + "-mbstring php" + version + "-xml php" + version + "-curl";
    }
}