# MySQL/MariaDB Plugin

The MySQL plugin allows you to install and manage a MariaDB database server on your VPS. It handles secure installation and provides helper commands for common database operations.

## Usage

```bash
mellow <target> mysql <command> [args...]
```

## Commands

| Command | Description | Example |
| :--- | :--- | :--- |
| `install` | Installs MariaDB and runs security script | `mellow prod mysql install` |
| `create-db` | Creates a new database | `mellow prod mysql create-db my_app_db` |
| `create-user` | Creates a new user (localhost access) | `mellow prod mysql create-user my_user "s3cr3t"` |
| `grant` | Grants all privileges on a DB to a user | `mellow prod mysql grant my_user my_app_db` |
| `status` | Checks service status | `mellow prod mysql status` |

## Quick Start

1.  **Install**:
    ```bash
    mellow prod mysql install
    ```
2.  **Create Database & User**:
    ```bash
    mellow prod mysql create-db my_blog
    mellow prod mysql create-user blog_user "secure_password"
    mellow prod mysql grant blog_user my_blog
    ```
