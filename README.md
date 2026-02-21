# Mellow

<div align="center">

<img src="./mellow-logo.png" width="200" alt="Mellow Logo">

**A CLI tool for Easy Server Management**

**SSH all the way**

[![Go Version](https://img.shields.io/badge/Go-1.21+-blue.svg)](https://golang.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](https://github.com/wasilwamark/mellow)

</div>

## About

Mellow manages your servers over SSH. Quick, standardized server configuration without complex IaC tools.

## Installation

**Prerequisites**

- Go 1.21+
- SSH access to your server

**Install**

```bash
git clone https://github.com/wasilwamark/mellow
cd mellow
make install
```

**Add Server & Command**

```bash
mellow alias add myserver user@1.2.3.4 --password 'password'
mellow myserver system update
```

## How It Works

Mellow connects via SSH, executes commands, and disconnects. Simple as that.

## Plugins

**Core**

- [System](internal/services/system): OS package management

**Services**

- [Nginx](internal/services/nginx): Web server
- [MySQL/MariaDB](internal/services/mysql): Database
- [Redis](internal/services/redis): Cache
- [Fail2Ban](internal/services/fail2ban): Security
- [Wireguard](internal/services/wireguard): VPN
- [Restic](internal/services/restic): Backup
- [Firewall](internal/services/firewall): Firewall (UFW/Firewalld)
- [WordPress](internal/services/wordpress): CMS
- [Keycloak](internal/services/keycloak): Identity
- [Docker](internal/services/docker): Containers
- [Runtimes](internal/services/runtimes): Language Runtimes

## Example Usage

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

## Contributing

Fork, branch, PR.
