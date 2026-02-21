# Keycloak Plugin Cheat Sheet

Quick reference for Keycloak plugin commands and common operations.

## Installation

```bash
# Basic installation
mellow myserver keycloak install

# Custom domain
mellow myserver keycloak install sso.example.com

# Install SSL
mellow myserver keycloak ssl sso.example.com
```

## Service Management

```bash
# Start/stop/restart
mellow myserver keycloak start
mellow myserver keycloak stop
mellow myserver keycloak restart

# Status and logs
mellow myserver keycloak status
mellow myserver keycloak logs
mellow myserver keycloak logs keycloak-db
```

## Realm Management

```bash
# List realms
mellow myserver keycloak realm list

# Create realm
mellow myserver keycloak realm create my-realm

# Delete realm
mellow myserver keycloak realm delete my-realm
```

## User Management

```bash
# List users (master realm)
mellow myserver keycloak user list

# Create user
mellow myserver keycloak user create username

# Create user in specific realm
mellow myserver keycloak user create username my-realm

# Reset password
mellow myserver keycloak user reset-password username
```

## Client Management

```bash
# List clients
mellow myserver keycloak client list

# Create client
mellow myserver keycloak client create client-name

# Create client in realm
mellow myserver keycloak client create client-name my-realm
```

## Backup & Restore

```bash
# Create backup
mellow myserver keycloak backup

# Restore from backup
mellow myserver keycloak restore /path/to/backup.tar.gz

# Interactive configuration
mellow myserver keycloak configure
```

## Common URLs

After installation:
- **Admin Console**: `https://your-domain/admin`
- **Base URL**: `https://your-domain`
- **API Docs**: `https://your-domain/realms/master/.well-known/openid_configuration`

## Files and Locations

- **Installation**: `/opt/keycloak`
- **Credentials**: `/opt/keycloak/credentials.txt`
- **Config**: `/opt/keycloak/docker-compose.yml`
- **Backups**: `/var/backups/keycloak/`
- **Nginx Config**: `/etc/nginx/sites-available/your-domain`

## Troubleshooting Commands

```bash
# Check all services
mellow myserver keycloak status

# Check Docker containers
mellow myserver docker ps

# Test HTTP response
curl -f http://your-domain/health/ready

# Test HTTPS response
curl -f https://your-domain/health/ready

# Check Nginx config
ssh myserver "nginx -t"

# View SSL certificates
ssh myserver "certbot certificates"
```

## Quick Setup Sequence

```bash
# 1. Install dependencies
mellow myserver docker install
mellow myserver nginx install

# 2. Install Keycloak
mellow myserver keycloak install sso.example.com

# 3. Configure SSL
mellow myserver keycloak ssl sso.example.com

# 4. Create realm for apps
mellow myserver keycloak realm create my-apps

# 5. Create admin user for realm
mellow myserver keycloak user create admin-user my-apps

# 6. Create OAuth client
mellow myserver keycloak client create web-app my-apps

# 7. Create backup
mellow myserver keycloak backup
```

## Environment Variables

Keycloak configuration can be modified in `/opt/keycloak/docker-compose.yml`:

```yaml
environment:
  KC_HOSTNAME: "sso.example.com"
  KC_PROXY: "edge"
  KC_CACHE: "local"
  KC_LOG_LEVEL: "INFO"
  KC_HOSTNAME_STRICT: "true"
  KC_HOSTNAME_STRICT_HTTPS: "true"
```

## Port Information

- **Keycloak Internal**: 8080 (container)
- **Nginx HTTP**: 80
- **Nginx HTTPS**: 443
- **PostgreSQL**: 5432 (internal to Docker network)

## Monitoring Commands

```bash
# Resource usage
mellow myserver keycloak status

# Docker stats
ssh myserver "docker stats"

# System resources
ssh myserver "free -h && df -h"

# Recent logs
mellow myserver keycloak logs | tail -50
```

## Security Checklist

- [ ] SSL certificate installed and valid
- [ ] Admin password changed from default
- [ ] Firewall rules configured
- [ ] Regular backups scheduled
- [ ] Monitoring and alerting set up
- [ ] Log rotation configured
- [ ] Software updates applied