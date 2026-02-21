# Redis Plugin

The Redis plugin provides comprehensive management for Redis database servers, including installation, configuration, service management, and backup operations.

## Overview

Redis is an open-source, in-memory data structure store used as a database, cache, and message broker. This plugin simplifies Redis deployment and management on VPS servers.

## Features

- **Installation**: One-click Redis server installation with automatic service configuration
- **Service Management**: Start, stop, restart, and check Redis service status
- **Configuration**: Interactive configuration wizard for Redis settings
- **Testing**: Built-in connection testing and basic operations verification
- **Monitoring**: Real-time server information and statistics
- **Backup**: Automated data backup with timestamp management
- **Security**: Password protection and network binding configuration

## Installation

```bash
# Install Redis server
mellow redis install

# Install with custom sudo password
mellow redis install --password=your_password
```

The plugin will:
- Update package lists
- Install Redis server and tools
- Enable Redis service for auto-start
- Verify installation

## Commands

### Service Management

```bash
# Start Redis service
mellow redis start

# Stop Redis service
mellow redis stop

# Restart Redis service
mellow redis restart

# Check service status
mellow redis status
```

### Configuration

```bash
# Interactive configuration
mellow redis configure
```

The configuration wizard allows you to:
- Set custom port (default: 6379)
- Configure network binding (localhost vs. all interfaces)
- Set authentication password
- Backup existing configuration

### Testing and Monitoring

```bash
# Test Redis connection and basic operations
mellow redis test

# Show detailed server information
mellow redis info
```

### Backup Management

```bash
# Create Redis data backup
mellow redis backup
```

Backups are stored in `/var/backups/redis/` with timestamp-based filenames.

### Removal

```bash
# Remove Redis server completely
mellow redis uninstall
```

This will:
- Stop and disable Redis service
- Remove Redis packages
- Clean up configuration and data directories

## Configuration Options

### Basic Settings
- **Port**: Redis server port (default: 6379)
- **Bind Address**: Network interface binding (localhost/0.0.0.0)
- **Password**: Authentication password for Redis access

### Configuration File
Redis configuration is stored at `/etc/redis/redis.conf`. The plugin creates automatic backups before making changes.

## Usage Examples

### Basic Setup
```bash
# Install Redis
mellow redis install

# Start the service
mellow redis start

# Test connection
mellow redis test

# Configure for production
mellow redis configure
```

### Production Deployment
```bash
# Install with security settings
mellow redis install
mellow redis configure  # Set password and bind to localhost

# Create backup schedule
mellow redis backup

# Monitor performance
mellow redis info
```

## Dependencies

- **System Plugin**: Required for package management and service control
- **Linux Platform**: Ubuntu/Debian-based systems
- **Sudo Access**: Administrative privileges for system operations

## Integration with Other Plugins

Redis is commonly used with:
- **WordPress**: For object caching
- **Nginx**: For HTTP caching
- **MySQL**: For query result caching
- **Application Servers**: For session storage

## Security Considerations

- Configure Redis to bind to localhost when not requiring remote access
- Set a strong authentication password
- Regularly backup Redis data
- Monitor Redis logs for unusual activity
- Keep Redis updated to latest stable version

## Troubleshooting

### Common Issues

**Service won't start:**
```bash
# Check status for detailed error
mellow redis status

# Check configuration syntax
redis-cli config get "*"
```

**Connection refused:**
```bash
# Verify Redis is listening
netstat -tlnp | grep redis

# Check firewall settings
sudo ufw status
```

**High memory usage:**
```bash
# Check memory usage
mellow redis info

# Configure maxmemory limit
mellow redis configure
```

### Log Files

- **Redis Log**: `/var/log/redis/redis-server.log`
- **System Log**: `journalctl -u redis-server`

## Performance Tuning

### Memory Optimization
- Set appropriate `maxmemory` limit
- Configure eviction policies
- Monitor memory usage regularly

### Network Optimization
- Use Unix sockets for local connections
- Configure TCP keepalive settings
- Set appropriate timeout values

## Monitoring and Maintenance

### Regular Tasks
```bash
# Daily status check
mellow redis status

# Weekly backup
mellow redis backup

# Monthly cleanup
redis-cli FLUSHDB  # Clear cache data
```

### Health Checks
The plugin provides built-in health monitoring:
- Service status verification
- Connection testing
- Memory usage tracking
- Client connection monitoring

## Plugin Metadata

- **Name**: redis
- **Version**: 1.0.0
- **Author**: Mellow Team
- **License**: MIT
- **Tags**: database, cache, redis, production-ready
- **Platforms**: linux/amd64, linux/arm64

## Support

For issues specific to Redis:
- [Redis Documentation](https://redis.io/documentation)
- [Redis Community](https://redis.io/community)

For Mellow plugin issues:
- Check plugin logs with `mellow redis status`
- Validate plugin with `mellow plugin validate`
- Review system logs for detailed error messages