# AGENTS.md

Guidance for AI agents (goose, Claude Code, Codex, and any others) working in this repository.

## Branch & Merge Workflow Rules — MANDATORY

- **Feature Branch Isolation:** Always perform work on dedicated feature branches (e.g., `feature/<task-name>`).
- **Fast-Forward Merge to `main`:** When work and testing are complete, checkout `main` and fast-forward merge the feature branch:
  ```bash
  git checkout main
  git merge --ff-only feature/<task-name>
  ```
- **Remote Push Restrictions:** Do **NOT** push feature branches to remote repositories (`origin` or `github`). Only push the `main` branch to remotes:
  ```bash
  git push origin main
  ```
- **CI Efficiency & Release Bundling:** Do **NOT** push to git/remotes recklessly on every small iteration or tweak — every push triggers CI builds and wastes pipeline minutes. Verify changes thoroughly locally, bundle related commits into logical releases, and push `main` only when a complete batch/feature is ready.

---

## Git Commit Attribution — MANDATORY

**NEVER attribute git commits to Claude, Codex, or any other agent/tool.**

- The **only** human author and committer for every commit is:
  `Mark Wasilwa <wasilwamark@outlook.com>`
- Format commit trailers exactly as:
  ```text
  Co-authored-by: Mark Wasilwa <wasilwamark@outlook.com>
  Signed-off-by: Mark Wasilwa <wasilwamark@outlook.com>
  ```
- Do **NOT** add "Generated with …", "Co-authored by Claude/Codex", or any similar attribution footer to commit messages or PR descriptions.
- Do **NOT** set `GIT_AUTHOR_*` / `GIT_COMMITTER_*` or `user.email` to anything other than `wasilwamark@outlook.com`.

Commit messages should describe the change only — no tooling signatures.

---

## Build Notes

Mellow is a **Java 25** CLI (no framework) that manages servers over SSH.
Coordinates are `com.acaciawave:mellow`; the base package is
`com.acaciawave.mellow`.

- **CLI / terminal:** JLine 3 powers the interactive shell, tab completion,
  history and prompts (`cli/Repl.java`, `cli/MellowCompleter.java`,
  `cli/Prompts.java`). There is **no plugin system** — every service is a plain
  `Provider` registered in `Providers.java`.
- **SSH:** Apache MINA SSHD (`ssh/SshConnection.java`); config lives in
  `~/.mellow/{aliases.json,secrets.json}` (secrets are `0600`).
- **Services:** `system`, `nginx`, `mysql`, `docker`, `fail2ban`, `firewall`,
  `keycloak`, `restic`, `runtimes`, and the core `alias` provider.
- **Build & test:**
  ```bash
  make            # or: mvn -DskipTests package   -> target/mellow.jar
  make test       # or: mvn test
  make run
  ```
  Maven must run on JDK 25 (`JAVA_HOME` must point at a JDK 25 install).
- **Native image** (optional, requires GraalVM for JDK 25):
  `make native` (fails fast with a clear message if the wrong `native-image`
  is on `PATH`). The jar targets Java 25 class files, so a JDK 21 GraalVM
  cannot build it.
- **Git hooks:** versioned under `.githooks/`; enable once per clone with
  `make hooks` (or `git config core.hooksPath .githooks`). `pre-commit` runs
  `mvn test`; `pre-push` mirrors pushes to `origin` (sr.ht) onto the `github`
  remote.

---

## Development Practices (TDD, Conventions)

- **Test-Driven Development (TDD):** Write tests before or alongside implementation. Follow Red-Green-Refactor. Ensure high coverage, especially for domain invariants and critical workflows.
- **Command providers:** keep handlers thin; extract pure logic (command
  builders, config/template rendering, parsing) into static helpers so it can be
  unit-tested without an SSH connection. Add such helpers to the matching
  `*ProviderTest`.
- **No plugin abstraction:** do not reintroduce a plugin loader/registry layer;
  register new services in `Providers.registry(...)`.
- **Secrets:** never print or log SSH/sudo passwords; keep `secrets.json`
  `0600`. Prefer the `ServiceSupport.runPrivileged` helper for sudo commands.
- **Errors:** surface failures as `MellowException` with actionable messages.
