# System Plugin

The **System** plugin manages core system updates and package maintenance on your VPS.

## Usage

All commands should be run against a target server alias (e.g., `ovh`) or connection string (`user@host`).

### Commands

| Command | Description | Example |
| :--- | :--- | :--- |
| `update` | Updates package lists (`apt-get update`) | `mellow prod system update` |
| `upgrade` | Upgrades installed packages | `mellow prod system upgrade` |
| `full-upgrade` | Performs dist-upgrade | `mellow prod system full-upgrade` |
| `autoremove` | Removes unused packages | `mellow prod system autoremove` |
| `install` | Installs specific packages | `mellow prod system install git curl` |
| `uninstall` | Removes specific packages | `mellow prod system uninstall apache2` |
| `shell` | Opens interactive SSH shell | `mellow prod system shell` |

### Sudo Privileges

These commands generally require root privileges. If your user is not `root`, you must provide the user's password so `sudo` can operate.

This is done via an environment variable specific to your server alias:

`SSH_SUDO_PWD_<ALIAS>`

**Example:**

If your alias is `ovh`:

```bash
export SSH_SUDO_PWD_OVH='your-secret-password'
mellow ovh system update
```

The tool will automatically detect the alias `ovh`, look for `SSH_SUDO_PWD_OVH`, and inject the password when running sudo commands.

**Method 2: Stored Secret (Recommended)**

You can save the password securely when adding the alias:

```bash
mellow alias add ovh user@host --password 'your-secret-password'
```

This saves the password to `~/.mellow/secrets.json` with restricted permissions. The tool will check this file if the environment variable is not set.
