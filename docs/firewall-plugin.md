# Firewall Plugin Documentation

## Overview

The firewall plugin provides comprehensive firewall management using UFW (Uncomplicated Firewall) for Ubuntu-based systems. It offers a simple interface for configuring, managing, and monitoring firewall rules.

## Features

- **UFW Installation**: Automatic installation and configuration of UFW
- **Rule Management**: Allow/deny traffic with flexible port and protocol options
- **Source IP Filtering**: Control access based on source IP addresses
- **Logging Configuration**: Configurable logging levels and verbosity
- **Safety Features**: SSH protection to prevent accidental lockouts
- **Rule Numbering**: Easy rule deletion with numbered rule listing
- **Status Monitoring**: Detailed firewall status and rule inspection

## Installation

```bash
mellow user@server firewall install
```

### Installation Options

| Option | Shorthand | Default | Description |
|--------|-----------|---------|-------------|
| `--default-policy` | `-p` | `deny` | Default firewall policy (allow/deny) |
| `--enable-logging` | `-l` | `true` | Enable firewall logging |
| `--allow-ssh` | | `true` | Automatically allow SSH connections |

### Examples

```bash
# Install with default settings (deny incoming, allow SSH)
mellow user@server firewall install

# Install with allow default policy
mellow user@server firewall install --default-policy allow

# Install without logging
mellow user@server firewall install --enable-logging=false

# Install without automatic SSH allowance
mellow user@server firewall install --allow-ssh=false
```

## Usage

### Allow Traffic

```bash
# Allow a specific port
mellow user@server firewall allow 80

# Allow a port with protocol
mellow user@server firewall allow 443 tcp

# Allow from specific IP
mellow user@server firewall allow 22 tcp 192.168.1.100

# Allow service names
mellow user@server firewall allow ssh
mellow user@server firewall allow http
mellow user@server firewall allow https
```

### Deny Traffic

```bash
# Deny a specific port
mellow user@server firewall deny 23

# Deny from specific IP
mellow user@server firewall deny 22 tcp 192.168.1.50

# Deny all traffic from IP range
mellow user@server firewall deny 192.168.1.0/24
```

### Firewall Management

```bash
# Enable firewall (activates all rules)
mellow user@server firewall enable

# Disable firewall (deactivates all rules)
mellow user@server firewall disable

# Show firewall status and rules
mellow user@server firewall status

# Reset firewall to defaults
mellow user@server firewall reset
```

### Rule Management

```bash
# Show numbered rules for deletion
mellow user@server firewall status

# Delete specific rule by number
mellow user@server firewall delete 3

# Configure logging
mellow user@server firewall logging on
mellow user@server firewall logging high
mellow user@server firewall logging off
```

## Logging Levels

UFW supports different logging levels:

- `on`: Standard logging
- `off`: No logging
- `low`: Minimal logging
- `medium`: Moderate logging
- `high`: Verbose logging
- `full`: Maximum logging

## Common Use Cases

### Basic Web Server Setup

```bash
# Install and configure firewall
mellow user@server firewall install

# Allow HTTP and HTTPS traffic
mellow user@server firewall allow http
mellow user@server firewall allow https

# Enable firewall
mellow user@server firewall enable
```

### Database Server Access

```bash
# Allow MySQL only from specific IP
mellow user@server firewall allow 3306 tcp 192.168.1.100

# Allow PostgreSQL from application server
mellow user@server firewall allow 5432 tcp 10.0.1.50
```

### SSH Security

```bash
# Restrict SSH to specific IP ranges
mellow user@server firewall delete 1  # Remove default SSH rule
mellow user@server firewall allow 22 tcp 192.168.1.0/24
mellow user@server firewall allow 22 tcp 10.0.0.0/8
```

### Development Environment

```bash
# Install with allow policy (more permissive for development)
mellow user@server firewall install --default-policy allow

# Add specific restrictions as needed
mellow user@server firewall deny 23    # Block telnet
mellow user@server firewall deny 3389  # Block RDP
```

## Safety Features

### SSH Protection

The plugin automatically includes SSH protection to prevent accidental lockouts:

1. **Installation**: By default allows SSH connections unless explicitly disabled
2. **Enable Check**: Verifies SSH rule exists before enabling firewall
3. **Warning Messages**: Clear warnings about potential lockout scenarios

### Rule Validation

- Validates rule syntax before application
- Provides clear error messages for invalid configurations
- Shows numbered rules for easy management

### Logging Integration

- Configurable logging levels for security monitoring
- Detailed status output for troubleshooting
- Integration with system logging

## Advanced Configuration

### Custom Policies

```bash
# More restrictive installation
mellow user@server firewall install \
  --default-policy deny \
  --enable-logging=true \
  --allow-ssh=false

# Then manually add specific rules
mellow user@server firewall allow 22 tcp 192.168.1.0/24
mellow user@server firewall allow 80
mellow user@server firewall allow 443
```

### Service-Specific Rules

```bash
# Web server rules
mellow user@server firewall allow 80    # HTTP
mellow user@server firewall allow 443   # HTTPS
mellow user@server firewall allow 8080  # Alternative HTTP

# Database rules
mellow user@server firewall allow 3306  # MySQL
mellow user@server firewall allow 5432  # PostgreSQL
mellow user@server firewall allow 6379  # Redis

# Development tools
mellow user@server firewall allow 3000  # Node.js apps
mellow user@server firewall allow 8080  # Java apps
mellow user@server firewall allow 9000  # Development servers
```

## Troubleshooting

### Common Issues

1. **Can't SSH after enabling firewall**
   ```bash
   # Check SSH rules
   mellow user@server firewall status

   # Add SSH rule if missing
   mellow user@server firewall allow ssh

   # Or allow from your specific IP
   mellow user@server firewall allow 22 tcp <your-ip>
   ```

2. **Service not accessible**
   ```bash
   # Check firewall status
   mellow user@server firewall status

   # Verify rule exists
   mellow user@server firewall status | grep <port>

   # Add rule if missing
   mellow user@server firewall allow <port>
   ```

3. **Resetting configuration**
   ```bash
   # Warning: This removes all rules
   mellow user@server firewall reset

   # Reconfigure with install
   mellow user@server firewall install
   ```

### Debugging

```bash
# Verbose status
mellow user@server firewall status

# Check numbered rules
mellow user@server firewall status | grep "\[.*\]"

# Enable logging for debugging
mellow user@server firewall logging full

# Check system logs
ssh user@server "tail -f /var/log/ufw.log"
ssh user@server "sudo ufw status verbose"
```

## Security Best Practices

1. **Default to Deny**: Use `--default-policy deny` for better security
2. **Specific IP Ranges**: Restrict SSH to specific IP ranges when possible
3. **Enable Logging**: Use logging to monitor firewall activity
4. **Regular Reviews**: Periodically review firewall rules
5. **Test Changes**: Always test firewall changes in non-production environments

## Integration with Other Plugins

The firewall plugin works well with other Mellow plugins:

```bash
# Install firewall with fail2ban
mellow user@server firewall install
mellow user@server fail2ban install

# Secure web server setup
mellow user@server firewall install
mellow user@server nginx install
mellow user@server firewall allow http
mellow user@server firewall allow https
mellow user@server firewall enable
```

## Plugin Metadata

- **Name**: firewall
- **Version**: 1.0.0
- **Author**: Mellow Team
- **License**: MIT
- **Repository**: github.com/wasilwamark/mellow-plugins/firewall
- **Tags**: security, networking, firewall, ufw
- **Dependencies**: system (>=1.0.0)
- **Platforms**: linux/amd64, linux/arm64

## Contributing

To contribute to the firewall plugin:

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Submit a pull request

## Support

For issues, questions, or contributions:
- GitHub Issues: [Repository Issues]
- Documentation: [Mellow Documentation]
- Community: [Discord/Slack Channel]