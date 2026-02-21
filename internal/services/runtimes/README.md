# Language Runtime Plugin

The Language Runtime plugin provides a unified interface for managing programming language runtime on your VPS. It supports multiple popular programming languages and handles version management using standard version managers.

## Supported Languages

- **Node.js** - Managed via NVM (Node Version Manager)
- **Python** - Managed via uv (ultra-fast Python package installer and resolver)
- **Go** - Direct installation
- **Java** - OpenJDK via apt (versions 8, 11, 17, 21)
- **Rust** - Managed via rustup
- **PHP** - Via apt and PPA (multiple versions)
- **Ruby** - Managed via rbenv
- **.NET** - Microsoft .NET SDK

## Commands

### Install a Language Runtime

```bash
# Install Node.js version 18
mellow <server> runtime install node 18

# Install Python 3.11
mellow <server> runtime install python 3.11

# Install Go 1.21
mellow <server> runtime install go 1.21

# Install Java 17
mellow <server> runtime install java 17

# Install Rust
mellow <server> runtime install rust latest

# Install PHP 8.2
mellow <server> runtime install php 8.2

# Install Ruby 3.2
mellow <server> runtime install ruby 3.2

# Install .NET 7
mellow <server> runtime install dotnet 7
```

### List Installed Runtime

```bash
mellow <server> runtime list
```

### Switch Between Versions

```bash
# Switch to Node.js 16
mellow <server> runtime use node 16

# Switch to Python 3.10
mellow <server> runtime use python 3.10
```

### Show Current Status

```bash
mellow <server> runtime status
```

### Remove a Runtime Version

```bash
# Remove Node.js 14
mellow <server> runtime remove node 14

# Remove Python 3.9
mellow <server> runtime remove python 3.9
```

### Update Version Managers

```bash
mellow <server> runtime update
```

## Features

### Automatic Version Managers

The plugin automatically installs and configures version managers for supported languages:

- **NVM** for Node.js - Allows installing multiple Node.js versions
- **uv** for Python - Ultra-fast Python package installer and resolver that manages Python versions
- **rbenv** for Ruby - Allows installing multiple Ruby versions
- **rustup** for Rust - Manages Rust toolchains

### Environment Setup

The plugin automatically:
- Updates shell profiles (`~/.bashrc`) with necessary environment variables
- Configures PATH for all installed runtime
- Sets up version-specific aliases where applicable

### Version Detection

The plugin can:
- Detect existing installations
- Show current active versions
- List all installed versions for each language

## Examples

### Setting up a Node.js Development Environment

```bash
# Install Node.js 18 with NVM
mellow myserver runtime install node 18

# Switch to Node.js 16 for a legacy project
mellow myserver runtime use node 16

# Check current status
mellow myserver runtime status
```

### Setting up a Python Environment

```bash
# Install Python 3.11 with uv
mellow myserver runtime install python 3.11

# List available Python versions
mellow myserver runtime list

# Install additional packages if needed (using uv)
mellow myserver system cmd "uv pip install virtualenv"

# Or use uv run for project-specific commands
mellow myserver system cmd "uv run python -m venv myenv"
```

### Multi-Language Setup

```bash
# Install multiple language runtime
mellow myserver runtime install node 18
mellow myserver runtime install python 3.11
mellow myserver runtime install go 1.21
mellow myserver runtime install java 17

# Check all installed runtime
mellow myserver runtime status
```

## Dependencies

The plugin requires the following system dependencies:
- `curl` - For downloading installers
- `wget` - For downloading packages
- `git` - For cloning version manager repositories

These dependencies are automatically checked and installed by the plugin.

## Notes

- For Go and Java, version switching is limited (requires reinstallation for Go, uses `update-alternatives` for Java)
- All installations are performed in the user's home directory when using version managers
- System-wide installations are performed for languages that support it (Java, PHP, .NET)
- The plugin respects existing installations and won't overwrite them unless explicitly requested