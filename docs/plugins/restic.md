# Restic Backup Plugin

The Restic plugin provides secure, efficient backups to S3-compatible storage. It supports streaming database dumps directly to the backup repository without creating local temporary files.

## Usage

```bash
mellow <target> restic <command> [args...]
```

## Commands

| Command | Description | Example |
| :--- | :--- | :--- |
| `install` | Install Restic | `mellow prod restic install` |
| `init` | Configure S3 repo & credentials | `mellow prod restic init` |
| `backup-db` | Stream database dump to S3 | `mellow prod restic backup-db my_app_db` |
| `snapshots` | List stored backups | `mellow prod restic snapshots` |
| `unlock` | Remove stale locks | `mellow prod restic unlock` |

## Setup Guide

1.  **Install**:
    ```bash
    mellow prod restic install
    ```
2.  **Initialize**:
    You will need your AWS/S3 Bucket URL, Access Key, and Secret Key.
    ```bash
    mellow prod restic init
    ```
    *This saves credentials to `/etc/mellow/restic.env` securely.*

3.  **Backup a Database**:
    ```bash
    mellow prod restic backup-db wp_my_site
    ```

4.  **Check Backups**:
    ```bash
    mellow prod restic snapshots
    ```
