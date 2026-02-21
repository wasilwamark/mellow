# Wireguard Plugin

The Wireguard plugin turns your VPS into a secure, personal VPN server. It handles installation, configuration, key generation, and client management with QR codes for easy mobile setup.

## Usage

```bash
mellow <target> wireguard <command> [args...]
```

## Commands

| Command | Description | Example |
| :--- | :--- | :--- |
| `install` | Installs Wireguard, tools, and QREncode | `mellow prod wireguard install` |
| `setup` | Configures the server, keys, and firewall | `mellow prod wireguard setup` |
| `add-peer` | Adds a client and **shows QR code** | `mellow prod wireguard add-peer my-phone` |
| `status` | Shows VPN interface status and connected peers | `mellow prod wireguard status` |
| `list-peers` | Lists configured peers (alias for status) | `mellow prod wireguard list-peers` |

## Quick Start

1.  **Install**:
    ```bash
    mellow prod wireguard install
    ```
2.  **Setup Server**:
    ```bash
    mellow prod wireguard setup
    ```
3.  **Connect a Phone**:
    *   Install Wireguard App on your phone.
    *   Run:
        ```bash
        mellow prod wireguard add-peer my-phone
        ```
    *   Scan the QR code that appears in your terminal.
