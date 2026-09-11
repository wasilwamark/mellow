# Mellow

<div align="center">

<img src="./mellow.png" width="200" alt="Mellow Logo">

**A CLI tool for Easy Server Management**

**SSH all the way**

[![Java](https://img.shields.io/badge/Java-25-blue.svg)](https://openjdk.org/projects/jdk/25/)
[![JLine](https://img.shields.io/badge/JLine-3.x-green.svg)](https://github.com/jline/jline3)
[![GraalVM](https://img.shields.io/badge/GraalVM-native--image-orange.svg)](https://www.graalvm.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

</div>

## About

Mellow manages your servers over SSH. Quick, standardized server configuration
without complex IaC tools. It connects over SSH, executes commands, and
disconnects — simple as that.

## Requirements

- **Java 25** (JDK)
- **Maven 3.9+**
- SSH access to your server

## Build

```bash
git clone https://github.com/wasilwamark/mellow
cd mellow
mvn package            # -> target/mellow.jar
```

Run it:

```bash
java -jar target/mellow.jar
```

### Native image (optional)

With [GraalVM for JDK 25](https://www.graalvm.org/):

```bash
mvn -Pnative package   # -> target/mellow (no JVM required)
```

## Usage

```bash
# Save a server alias (password used for SSH auth and sudo)
mellow alias add myserver user@1.2.3.4 --password 'password'

# Run a service command
mellow myserver system update
```

Running `mellow` with no arguments opens an interactive JLine shell with tab
completion and persistent history.

## Supported Services

Mellow includes built-in command providers for managing services. To see all
available commands for a service, run `mellow [server] [service] --help`.

| Service | Description | Usage Example |
| :--- | :--- | :--- |
| **System** | OS package management | `mellow <server> system update` |
| **Nginx** | Web server | `mellow <server> nginx install` |
| **MySQL** | Database | `mellow <server> mysql install` |
| **Fail2Ban** | Security/Intrusion prevention | `mellow <server> fail2ban install` |
| **Restic** | Backups | `mellow <server> restic init` |
| **Firewall** | Firewall (UFW/Firewalld) | `mellow <server> firewall allow 80` |
| **Keycloak** | Identity provider | `mellow <server> keycloak install` |
| **Docker** | Containerization | `mellow <server> docker install` |
| **Runtimes**| Language Runtimes | `mellow <server> runtime install node 18` |

## Common Commands

```bash
# System updates
mellow myserver system update
mellow myserver system upgrade

# Web server
mellow myserver nginx install
mellow myserver nginx install-ssl mydomain.com

# Database
mellow myserver mysql install
mellow myserver mysql create-db myapp

# Firewall
mellow myserver firewall install
mellow myserver firewall allow 80
```

## Development

```bash
make test          # or: mvn test
make coverage      # tests + JaCoCo report (target/site/jacoco)
make package       # or: mvn -DskipTests package
make run           # build and launch
```

### Git hooks

Hooks live in `.githooks/`. Enable them once per clone:

```bash
git config core.hooksPath .githooks
```

- `pre-commit` runs `mvn test`.
- `pre-push` mirrors a push to `origin` onto the `github` remote.

See [docs/PLAN.md](docs/PLAN.md) for the architecture and porting notes.
