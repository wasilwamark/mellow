# Mellow → Java 25 Port Plan

**Status:** Port complete (Phases 0–4) · Go source removed  
**Target:** Java 25 (LTS) · JLine 3.x · GraalVM native-image  
**Coordinates:** `com.acaciawave:mellow` · base package `com.acaciawave.mellow`  
**Source material:** the original Go codebase, now fully removed — see git history.

---

## 0. Progress

| Area | Status | Where |
| :--- | :--- | :--- |
| Maven / Java 25 scaffold + shaded runnable jar | ✅ | `pom.xml`, `Makefile` |
| Command model (`Command`/`Provider`/`Registry`/`Dispatcher`/`FlagParser`) | ✅ | `src/main/java/com/acaciawave/mellow/cli` |
| Config (`~/.mellow` aliases/secrets, `0600`) | ✅ | `com/acaciawave/mellow/config` |
| Distro detection + package-manager abstraction | ✅ | `com/acaciawave/mellow/distro`, `com/acaciawave/mellow/pkgmgr` |
| SSH layer on Apache MINA SSHD | ✅ compiles · ⏳ live integration test | `com/acaciawave/mellow/ssh` |
| `alias` provider (local) | ✅ | `com/acaciawave/mellow/command/alias` |
| `system` provider | ✅ | `com/acaciawave/mellow/command/system` |
| `nginx` provider | ✅ | `com/acaciawave/mellow/command/nginx` |
| `mysql` provider | ✅ | `com/acaciawave/mellow/command/mysql` |
| `docker` provider | ✅ | `com/acaciawave/mellow/command/docker` |
| `fail2ban` provider | ✅ | `com/acaciawave/mellow/command/fail2ban` |
| `firewall` provider | ✅ | `com/acaciawave/mellow/command/firewall` |
| `restic` provider | ✅ | `com/acaciawave/mellow/command/restic` |
| `runtimes` provider | ✅ | `com/acaciawave/mellow/command/runtimes` |
| `keycloak` provider | ✅ | `com/acaciawave/mellow/command/keycloak` |
| Unit tests (69) | ✅ | `src/test/java` |
| SSH integration test (live) | ⏳ Phase 1 (no Docker here) | — |
| JLine interactive REPL + completion | ✅ | `com/acaciawave/mellow/cli/Repl.java` |
| GraalVM native image | ⛔ Phase 5 (needs GraalVM for JDK 25) | not yet installed |
| CI / release matrix | ⛔ Phase 6 | — |

**Run it:**

```bash
mvn -DskipTests package          # -> target/mellow.jar
$JAVA_HOME/bin/java -jar target/mellow.jar
# or: make package test run
```

**Immediate next steps:** live SSH integration test against a Testcontainers
`sshd` container, then the JLine interactive REPL (Phase 4).

---

## 1. Why / Scope

Mellow is being rewritten from Go to Java 25. The CLI stays a single static
binary (`mellow`) that manages servers over SSH.

### 1.1 Changes already applied to the Go tree (source of truth for the port)

| Change | Impact |
| :--- | :--- |
| Deleted services: `kong`, `redis`, `wireguard`, `wordpress` | Not ported. 9 services remain. |
| Deleted the plugin system (`pkg/plugin`, `internal/core/plugin-manager`, cobra-based plugin registration) | The Java port uses a **direct command registry** — no plugin loader, no metadata/validation layers. |
| Services are now plain `api.Provider` structs (`Name()`, `Description()`, `Commands()`) | Map 1:1 to Java commands. |

### 1.2 Services kept (to port)

| Service | Go package | Commands (approx.) |
| :--- | :--- | :--- |
| `system` | `internal/services/system` | update, upgrade, full-upgrade, autoremove, shell, install, uninstall |
| `nginx` | `internal/services/nginx` | install, status, start, stop, restart, reload, logs, list-sites, add-site, remove-site, install-ssl |
| `mysql` | `internal/services/mysql` | install, create-db, create-user, grant, status |
| `docker` | `internal/services/docker` | install, status, compose, … |
| `fail2ban` | `internal/services/fail2ban` | install, status, ban, unban, … |
| `firewall` | `internal/services/firewall` | install, allow, deny, status, logging, … |
| `keycloak` | `internal/services/keycloak` | install, uninstall, status, realm, user, client, ssl, backup, restore, configure |
| `restic` | `internal/services/restic` | install, init, backup-db, snapshots, restore-db, unlock |
| `runtimes` | `internal/services/runtimes` | install node/python/go/java/… by version |
| `alias` (core) | `internal/core/alias` | add, list, remove |

### 1.3 Goals

- Feature parity with the 9 remaining services + alias management.
- Same UX and CLI surface: `mellow [alias|user@host] service command [args]`.
- Additional interactive mode: bare `mellow` opens a JLine REPL (tab completion,
  history, context-aware prompts) — the main reason JLine is chosen over picocli
  or a plain `parse-and-run` loop.
- Single self-contained native binary (GraalVM) — replaces Go cross-compilation.

### 1.4 Non-goals

- Dynamic/external plugin loading (plugin system removed by design).
- Windows support at parity (Go only shipped linux/darwin in goreleaser).
- Kong, Redis, WireGuard, WordPress services.

---

## 2. Target technology stack

| Concern | Choice | Notes |
| :--- | :--- | :--- |
| Language | **Java 25** (JDK 25, Sept 2025 LTS) | Records/sealed types/pattern matching for the command model; `ProcessBuilder` for local exec; no extra modules needed beyond `java.base`. |
| Build | **Maven** (3.9+) | `maven-compiler-plugin` `--release 25`, `maven-surefire` for tests. |
| CLI / terminal | **JLine 3.29+** | `LineReader`, `Terminal`, `Completer`, `History`; also `MaskingCallback` for password prompts; colors without ANSI hardcoding. |
| SSH | **Apache MINA SSHD 2.15+** | Pure-Java SSH client replaces shelling out to the `ssh` binary; keeps password + key auth, exec channels, PTY for `shell`/interactive commands. |
| JSON config | **Jackson 2.19** (databind + jackson-databind) | `~/.mellow/aliases.json`, `~/.mellow/secrets.json` (0600). |
| Testing | **JUnit 5, AssertJ, Testcontainers-Java** | SSHD container + distro containers (mirrors `tests/integration`). |
| Native image | **GraalVM for JDK 25 (25.x)** + `native-maven-plugin` | Static-ish binary; `--initialize-at-build-time` for config classes. |
| CI / release | GitHub Actions + goreleaser replaced by native-image matrix | linux + darwin, amd64 + arm64. |

### 2.1 JLine vs. current Go CLI

- Go today: cobra parses `os.Args`; interactivity is ad-hoc `fmt.Scanln`
  (e.g. nginx `install-ssl` site selection, keycloak `configure`).
- Java: JLine owns all terminal I/O. One-shot mode runs through the same
  dispatcher; interactive mode reuses the same command objects with completers.

---

## 3. Target architecture (`src/main/java/com/acaciawave/mellow`)

```
com/acaciawave/mellow
├── Main.java                     # native-image entrypoint; dispatch one-shot or REPL
├── cli/
│   ├── CommandContext.java       # record(context, ssh connexion?, args, flags)
│   ├── CommandHandler.java       # @FunctionalInterface: Result run(CommandContext)
│   ├── Command.java              # record(name, description, args, flags, handler)
│   ├── Argument.java / Flag.java / ArgumentType.java
│   ├── Provider.java             # interface { name(); description(); commands(); }
│   ├── Registry.java             # LinkedHashMap<String, Provider>; register(...)
│   ├── Dispatcher.java           # resolve target -> connect -> run handler; error mapping
│   ├── Repl.java                 # JLine LineReader loop, completers, history
│   └── Completion.java           # completers: aliases, services, commands, flags
├── ssh/
│   ├── SshConfig.java            # host, user, port, password, identityFile, timeout
│   ├── SshConnection.java        # implements Connection (see §5); MINA SSHD client
│   └── SshConnector.java         # fail-fast connect + distro probe
├── config/
│   ├── MellowConfig.java         # ~/.mellow dir, aliases.json, secrets.json (0600)
│   └── SecretsStore.java
├── distro/
│   ├── OsRelease.java            # parse /etc/os-release
│   └── DistroInfo.java           # record(id, name, family, pkgMgr, serviceMgr)
├── pkgmgr/
│   └── PackageManager.java       # interface + Apt/Dnf/Yum/Pacman/Apk impls
├── command/                      # one package per service (ports of the Go providers)
│   ├── system/SystemProvider.java
│   ├── nginx/NginxProvider.java
│   ├── mysql/MysqlProvider.java
│   ├── docker/DockerProvider.java
│   ├── fail2ban/Fail2banProvider.java
│   ├── firewall/FirewallProvider.java
│   ├── keycloak/KeycloakProvider.java
│   ├── restic/ResticProvider.java
│   ├── runtimes/RuntimesProvider.java
│   └── alias/AliasProvider.java
└── util/
    └── Output.java               # status lines (✔/ℹ/⚡ emoji parity), exit codes
```

### 3.1 Command model mapping (Go → Java)

| Go (current tree) | Java |
| :--- | :--- |
| `api.Command{Name, Description, Args, Flags, Handler}` | `record Command(String name, String description, List<Argument> args, List<Flag> flags, CommandHandler handler)` |
| `CommandHandler(ctx, conn, args, flags) error` | `CommandHandler.run(CommandContext)` returning `CommandResult` (or throwing `MellowException`) |
| `api.Result{Success, Stdout, Stderr, ExitCode, Duration}` | `record CommandResult(boolean success, String stdout, String stderr, int exitCode, Duration duration)` with helpers `lines()`, `contains(...)` |
| `api.Provider` + `cli.Registry` | `Provider` interface + `Registry` (registration in `Main`) |
| `flags map[string]interface{}` (`"password"`, `"strict"`, …) | `Map<String,Object> flags` on `CommandContext` (kept generic; typed helpers where useful) |

---

## 4. CLI design (JLine) — detail

### 4.1 One-shot mode (identical command surface)

```
mellow myserver system update
mellow mark@1.2.3.4 nginx add-site example.com --proxy 3000
mellow alias add ovh ubuntu@1.2.3.4 --password '…'
```

Parsing stays positional (target, provider, command, args) exactly like
`executeDirectCommand` in `internal/cli/root.go`. JLine is only used where the
current Go CLI prompts:
- `nginx install-ssl` without a domain → menu prompt
- `keycloak configure` → interactive Q&A
- password entry → `MaskingCallback` (masked input, no echo)
- Askpass: env `MELLOW_ASKPASS_MODE` logic is dropped — MINA SSHD handles
  passwords in-process (no `SSH_ASKPASS` hack needed).

### 4.2 Interactive mode (new — JLine's reason to exist)

`mellow` with no args opens a REPL:

```
mellow> myserver system update
mellow> myserver nginx [TAB]              → install status start stop restart reload logs list-sites add-site remove-site install-ssl
mellow> myserver nginx install-ssl [TAB]  → <sites fetched over SSH> or <domain completions>
mellow> alias add [TAB]                   → <existing aliases>
mellow> [TAB]                             → commands, aliases, user@host patterns
```

- `LineReader` with `history` persistence to `~/.mellow/history`.
- `AggregateCompleter`: `AliasCompleter` + `ProviderCompleter` + `CommandCompleter`
  (command lists loaded from the Registry; site/domain completions fetched per
  connection).
- Errors from `Dispatcher` render through `Output` (emoji parity) and print the
  failing command’s usage.
- `Ctrl-C` cancels the running SSH command gracefully (`Session.close(true)`).

---

## 5. SSH layer — MINA SSHD

The Go version spawns the OS `ssh` binary per command. Java uses a persistent
MINA SSHD client per invocation.

| Go (`internal/ssh`) | Java |
| :--- | :--- |
| `exec.Command("ssh", ...)` per call | one `ClientSession` per command run; exec channels per command |
| `RunSudo(cmd, password)` = `echo pass \| sudo -S …` | same remote command string (keep) |
| `RunInteractive`/`Shell` with TTY | `ChannelExec` with `-t` (pty) → wire stdin/stdout; `ChannelsSupportPty` |
| `setupAskPass` env hack | in-process auth: `PasswordIdentityProvider` / `KeyIdentityProvider` (default `~/.ssh/id_*`) |
| file ops via `printf`/`cp`/`mv` strings | keep remote-command approach (already string-based); add `SFTP` channel as an optional optimization for `UploadFile`/`DownloadFile` |
| `Result` | `CommandResult` |

Timeout semantics preserved: `ConnectTimeout=10`, `ServerAliveInterval=30` →
`SshClient` connection timeout + keepalive (`ClientSession.sendKeepAliveMsg`).

---

## 6. GraalVM native-image strategy

- Build with **GraalVM for JDK 25** (`gu install native-image` or the
  `native-maven-plugin` `native` profile), module `org.graalvm.buildtools:native-maven-plugin`.
- Known native-image surface:
  - **Jackson**: `reflect-config.json` for `MellowConfig`/secrets records; or
    avoid reflection entirely by using Jackson with explicit `ObjectMapper`
    reader/generator — prefer records + `@JsonProperty` and a `reflect-config`
    entry per class.
  - **JLine**: ships GraalVM-friendly (`TerminalProvider` discovery via
    `META-INF/services` — needs `resource-config.json` for `org.jline.*`), plus
    `-H:+AddAllCharsets` for terminal encoding.
  - **MINA SSHD**: keep defaults (`DefaultCipherFactories` etc. are pure Java,
    no BouncyCastle unless exotic keys — avoid BC initially); add
    `--enable-native-access` not required (JNA not used).
  - `parse-only` build for CI smoke test; `PGO` later (optional).
- Deliverables:
  - `mellow` (linux/amd64, linux/arm64, darwin/amd64, darwin/arm64) via GitHub
    Actions matrix; `gh`-release on tag (replaces `.goreleaser.yaml`).
  - Size/startup targets: ≤ ~60 MB, cold start < 100 ms.

---

## 7. Migration phases

### Phase 0 — Scaffold (½ day)
- [x] Repository layout `src/main/java`/`src/test/java`, `pom.xml`
- [x] Java 25 toolchain (`maven.compiler.release=25`)
- [ ] Java 25 toolchain pin (`.java-version` / `.sdkmanrc`)
- [ ] Dependencies resolved; **native-image smoke test** (`mvn -Pnative` hello-world) — blocked on GraalVM for JDK 25
- [x] Gradle? Decide **Maven** (native-maven-plugin maturity)

### Phase 1 — Core plumbing
- [x] `Command`/`Provider`/`Registry`/`Dispatcher` port (from `internal/api` + `internal/cli`)
- [x] `config` (aliases.json/secrets.json parity, incl. env fallback `SSH_PWD_<ALIAS>`)
- [x] `ssh` layer with MINA SSHD: connect, `RunCommand`, `RunSudo`, `WriteFile`, distro probe
- [ ] **SSH integration test against a Testcontainers `sshd` container**
- [x] `distro` + `pkgmgr` port
- [x] `Output`/emoji parity; exit codes (0 ok, 1 error)

### Phase 2 — system service (reference provider)
- [x] `system` provider: 7 commands, package-manager/checkSudo helpers
- [ ] Golden acceptance: `mellow <c> system update/upgrade/install …` (needs live host)

### Phase 3 — Remaining services (parallelizable, port provider-per-commit)
- [x] `alias`, `nginx`, `mysql`, `docker`, `fail2ban`, `firewall`, `restic`, `runtimes`, `keycloak`
- [x] For each: commands table + unit tests for pure helpers
- [ ] Live integration test per service (see §8)

### Phase 4 — JLine interactive mode
- [x] `Repl` + completers (services, commands, aliases)
- [x] History, Ctrl-C handling, shell-style quoting
- [ ] Dynamic site/domain completion fetched over SSH (future)
- [x] Replaced ad-hoc `fmt.Scanln` flows with JLine-backed `Prompts`

### Phase 5 — GraalVM native image
- [ ] Full `native-image` build with correct `reflect/resource-config.json`
- [ ] JLine + MINA SSHD + Jackson verified under native
- [ ] `mvn -Pnative test` (JUnit runs on JVM; native verified via CLI smoke suite)

### Phase 6 — CI / release
- [ ] GitHub Actions: build native 4-platform matrix, unit + integration tests, release on tags
- [ ] Docs update (README parity), `CHANGELOG`

## 8. Testing strategy

| Layer | Tooling | Coverage |
| :--- | :--- | :--- |
| Unit | JUnit 5 + AssertJ | Registry/dispatcher; OsRelease parsing; PackageManager command builders; nginx site config generation; restic env rendering |
| SSH | Testcontainers `linuxserver/openssh-server` or `panubo/sshd` | connect, auth, RunSudo, file ops, pty shell |
| Integration | Testcontainers `debian` / `ubuntu` / `alpine` containers (mirrors `tests/integration`) | install/status flows per service, sudo paths |
| CLI | JLine `Terminal` in test mode + golden files | output/emoji parity, exit codes |
| Native | post-build smoke script | same CLI suite against the native binary |

Moved/kept from Go: `Makefile test` → `mvn verify`; `Makefile build-all` →
`mvn -Pnative` matrix.

## 9. Risks & mitigations

| Risk | Mitigation |
| :--- | :--- |
| MINA SSHD under GraalVM (reflection on crypto/key types) | Constrain key types (RSA/EdDSA/ecdsa), keep `default` factories, verify in Phase 1, add `reflect-config` promptly |
| JLine in non-TTY (CI, piped stdout) | Use `TerminalBuilder` `dumb` fallback; one-shot mode must never crash without a TTY |
| Emoji/unicode width in JLine rendering | `-Dorg.jline.terminal.dumb=true` fallback; width-aware rendering via JLine `AttributedString` |
| Interactive prompts break scripts | Only prompt when stdin is a TTY and no `--non-interactive` equivalent (mirror Go behavior) |
| Password prompting secrets leaking in `history` | LineReader history disabled/redacted for masked prompts |
| Native build time on CI | Cache GraalVM; parse-only checks on PRs; native matrix only on main/tags |
| Config compat break | Keep `~/.mellow` JSON file formats byte-compatible |

## 10. Acceptance criteria

1. `mellow <server> <service> <command>` behaves identically to the pruned Go tree for all 9 services + alias.
2. `mellow` REPL offers tab completion and history; passwords masked.
3. Native binaries for linux/darwin × amd64/arm64 build in CI; no JVM install required.
4. `go test`/integration equivalents pass under Java tooling (`mvn verify`).

*Generated from the Go tree at commit state after plugin-system removal; revisit
plan when the Java scaffold lands (Phase 0).*