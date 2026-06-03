# Mellow

<div align="center">

<img src="./mellow.png" width="200" alt="Mellow Logo">

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

## Supported Services

Mellow includes a variety of plugins for managing services. To see all available commands for a service, run `mellow [server] [service] --help`.

| Service | Description | Usage Example |
| :--- | :--- | :--- |
| **System** | OS package management | `mellow <server> system update` |
| **Nginx** | Web server | `mellow <server> nginx install` |
| **MySQL** | Database | `mellow <server> mysql install` |
| **Redis** | In-memory store | `mellow <server> redis install` |
| **Fail2Ban** | Security/Intrusion prevention | `mellow <server> fail2ban install` |
| **Wireguard** | VPN management | `mellow <server> wireguard install` |
| **Restic** | Backups | `mellow <server> restic init` |
| **Firewall** | Firewall (UFW/Firewalld) | `mellow <server> firewall allow 80` |
| **WordPress**| CMS | `mellow <server> wordpress install` |
| **Keycloak** | Identity provider | `mellow <server> keycloak install` |
| **Docker** | Containerization | `mellow <server> docker install` |
| **Kong** | API Gateway | `mellow <server> kong install` |
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