package com.acaciawave.mellow.command.docker;

import java.util.List;
import java.util.StringJoiner;

import com.acaciawave.mellow.MellowException;
import com.acaciawave.mellow.cli.Argument;
import com.acaciawave.mellow.cli.Command;
import com.acaciawave.mellow.cli.CommandContext;
import com.acaciawave.mellow.cli.CommandHandler;
import com.acaciawave.mellow.cli.Provider;
import com.acaciawave.mellow.command.ServiceSupport;
import com.acaciawave.mellow.ssh.CommandResult;
import com.acaciawave.mellow.util.Output;

/** Docker Engine & Compose provider (port of {@code internal/services/docker}). */
public final class DockerProvider implements Provider {

    @Override
    public String name() {
        return "docker";
    }

    @Override
    public String description() {
        return "Manage Docker Engine & Compose";
    }

    @Override
    public List<Command> commands() {
        return List.of(
                Command.of("install", "Install Docker Engine & Compose", this::install),
                Command.of("status", "Check Docker status",
                        context -> context.connection().runInteractive("systemctl status docker")),
                Command.of("compose", "Run docker compose commands", this::compose),
                Command.of("ps", "List running containers", simpleDocker("ps")),
                Command.builder("logs", "Stream container logs", this::logs)
                        .arg(Argument.optional("container", "Container name (omit for compose logs)"))
                        .build(),
                Command.of("prune", "Prune unused docker resources", simpleDocker("system prune -f")),
                Command.of("verify", "Verify installation (hello-world)", simpleDocker("run --rm hello-world")),
                // Compose shortcuts
                Command.of("up", "Start services (docker compose up -d)", simpleCompose("up -d")),
                Command.of("down", "Stop services (docker compose down)", simpleCompose("down")),
                Command.of("pull", "Pull images (docker compose pull)", simpleCompose("pull")));
    }

    private void install(CommandContext context) {
        Output.plain("🐳 Installing Docker...");
        String password = ServiceSupport.password(context);

        CommandResult result = context.connection()
                .runSudo("curl -fsSL https://get.docker.com | sh", password);
        if (!result.success()) {
            throw new MellowException("failed to install docker: " + result.stderr());
        }

        Output.plain("👤 Adding user to docker group...");
        CommandResult group = context.connection().runSudo("usermod -aG docker $USER", password);
        if (!group.success()) {
            Output.warn("Failed to add user to docker group: " + group.stderr());
        } else {
            Output.success("User added to docker group (requires re-login to take effect)");
        }

        Output.success("Docker installed successfully!");
    }

    private void compose(CommandContext context) {
        String command = "docker compose " + String.join(" ", context.args());
        context.connection().runInteractive(command.strip());
    }

    private void logs(CommandContext context) {
        if (context.argCount() < 1) {
            Output.plain("📜 Streaming docker compose logs...");
            context.connection().runInteractive("docker compose logs -f");
            return;
        }
        context.connection().runInteractive("docker logs -f " + context.arg(0));
    }

    private CommandHandler simpleDocker(String subcommand) {
        return context -> context.connection().runInteractive("docker " + subcommand);
    }

    private CommandHandler simpleCompose(String subcommand) {
        return context -> {
            StringJoiner joiner = new StringJoiner(" ");
            joiner.add("docker").add("compose").add(subcommand);
            context.args().forEach(joiner::add);
            context.connection().runInteractive(joiner.toString().strip());
        };
    }
}