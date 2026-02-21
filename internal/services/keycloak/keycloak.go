package keycloak

import (
	"context"
	"fmt"
	"math/rand"
	"strings"
	"time"

	"github.com/wasilwamark/mellow/pkg/plugin"
)

func (p *Plugin) installHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	sudoPass := getSudoPass(flags)

	fmt.Println("🔐 Installing Keycloak...")

	// Parse installation options
	domain := "keycloak.local"
	if len(args) > 0 {
		domain = args[0]
	}

	// Check if Keycloak is already installed
	keycloakDir := "/opt/keycloak"
	if result := conn.RunCommand(fmt.Sprintf("test -d %s", keycloakDir), plugin.WithHideOutput()); result.Success {
		fmt.Println("⚠️  Keycloak is already installed")
		fmt.Printf("Installation directory: %s\n", keycloakDir)
		return nil
	}

	// Check dependencies
	fmt.Println("🔍 Checking dependencies...")

	// Check Docker
	if result := conn.RunCommand("docker --version", plugin.WithHideOutput()); !result.Success {
		return fmt.Errorf("Docker is not installed. Please install Docker first: mellow docker install")
	}

	// Check Docker Compose
	if result := conn.RunCommand("docker-compose --version", plugin.WithHideOutput()); !result.Success {
		return fmt.Errorf("Docker Compose is not installed. Please install Docker Compose first")
	}

	// Generate secure passwords
	dbPassword := generateRandomPassword(32)
	adminPassword := generateRandomPassword(32)

	fmt.Println("📁 Creating installation directory...")
	if result := conn.RunSudo(fmt.Sprintf("mkdir -p %s", keycloakDir), sudoPass); !result.Success {
		return fmt.Errorf("failed to create installation directory: %s", result.Stderr)
	}

	// Create docker-compose.yml
	fmt.Println("📝 Creating Docker Compose configuration...")
	dockerComposeContent := fmt.Sprintf(dockerComposeTemplate, dbPassword, dbPassword, adminPassword, domain)

	if err := conn.WriteFile(dockerComposeContent, fmt.Sprintf("%s/docker-compose.yml", keycloakDir)); err != nil {
		return fmt.Errorf("failed to create docker-compose.yml: %v", err)
	}

	// Set ownership
	if result := conn.RunSudo(fmt.Sprintf("chown -R $USER:$USER %s", keycloakDir), sudoPass); !result.Success {
		return fmt.Errorf("failed to set ownership: %s", result.Stderr)
	}

	// Start services
	fmt.Println("🚀 Starting Keycloak services...")
	if result := conn.RunCommand(fmt.Sprintf("cd %s && docker-compose up -d", keycloakDir), plugin.WithHideOutput()); !result.Success {
		return fmt.Errorf("failed to start services: %s", result.Stderr)
	}

	// Wait for Keycloak to be ready
	fmt.Println("⏳ Waiting for Keycloak to start...")
	if err := p.waitForKeycloakReady(conn); err != nil {
		return fmt.Errorf("Keycloak failed to start: %v", err)
	}

	// Configure Nginx reverse proxy
	fmt.Println("🌐 Configuring Nginx reverse proxy...")
	nginxConfig := fmt.Sprintf(nginxTemplate, domain)
	nginxConfigPath := fmt.Sprintf("/etc/nginx/sites-available/%s", domain)

	// Write nginx config to temp file first
	tempNginxPath := fmt.Sprintf("/tmp/%s.conf", domain)
	if err := conn.WriteFile(nginxConfig, tempNginxPath); err != nil {
		return fmt.Errorf("failed to write nginx config: %v", err)
	}

	// Move to sites-available and enable
	nginxCmds := []string{
		fmt.Sprintf("mv %s %s", tempNginxPath, nginxConfigPath),
		fmt.Sprintf("ln -sf %s /etc/nginx/sites-enabled/", nginxConfigPath),
	}

	for _, cmd := range nginxCmds {
		if result := conn.RunSudo(cmd, sudoPass); !result.Success {
			return fmt.Errorf("failed to configure nginx: %s", result.Stderr)
		}
	}

	// Test nginx config
	if result := conn.RunSudo("nginx -t", sudoPass); !result.Success {
		fmt.Println("⚠️  Nginx config test failed, removing configuration...")
		conn.RunSudo(fmt.Sprintf("rm -f %s /etc/nginx/sites-enabled/%s", nginxConfigPath, domain), sudoPass)
		return fmt.Errorf("nginx configuration error: %s", result.Stderr)
	}

	// Reload nginx
	if result := conn.RunSudo("systemctl reload nginx", sudoPass); !result.Success {
		return fmt.Errorf("failed to reload nginx: %s", result.Stderr)
	}

	// Save credentials to file
	credentialsContent := fmt.Sprintf(`Keycloak Installation Details
================================
Domain: %s
Admin Username: admin
Admin Password: %s
Database Password: %s

Admin Console: http://%s/admin
API Documentation: http://%s/realms/master/.well-known/openid_configuration

Installation Date: %s
`, domain, adminPassword, dbPassword, domain, domain, time.Now().Format("2006-01-02 15:04:05"))

	credentialsFile := fmt.Sprintf("%s/credentials.txt", keycloakDir)
	if err := conn.WriteFile(credentialsContent, credentialsFile); err != nil {
		fmt.Printf("⚠️  Failed to save credentials file: %v\n", err)
	}

	// Set file permissions
	if result := conn.RunSudo(fmt.Sprintf("chmod 600 %s", credentialsFile), sudoPass); !result.Success {
		fmt.Printf("⚠️  Failed to set credentials file permissions\n")
	}

	fmt.Println("✅ Keycloak installed successfully!")
	fmt.Printf("\n🎉 Installation Complete!\n")
	fmt.Printf("📁 Installation Directory: %s\n", keycloakDir)
	fmt.Printf("🌐 Access URL: http://%s\n", domain)
	fmt.Printf("👤 Admin Console: http://%s/admin\n", domain)
	fmt.Printf("🔑 Admin Credentials saved to: %s\n", credentialsFile)
	fmt.Printf("\n⚠️  Important:\n")
	fmt.Printf("- Store the admin password securely\n")
	fmt.Printf("- Configure SSL after installation: mellow keycloak ssl %s\n", domain)
	fmt.Printf("- Update DNS to point %s to this server\n", domain)

	return nil
}

func (p *Plugin) uninstallHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	sudoPass := getSudoPass(flags)

	fmt.Println("🗑️  Uninstalling Keycloak...")

	keycloakDir := "/opt/keycloak"

	// Check if Keycloak is installed
	if result := conn.RunCommand(fmt.Sprintf("test -d %s", keycloakDir), plugin.WithHideOutput()); !result.Success {
		return fmt.Errorf("Keycloak is not installed")
	}

	// Stop and remove containers
	fmt.Println("🛑 Stopping and removing containers...")
	if result := conn.RunCommand(fmt.Sprintf("cd %s && docker-compose down -v", keycloakDir), plugin.WithHideOutput()); !result.Success {
		fmt.Printf("⚠️  Failed to stop containers: %s\n", result.Stderr)
	}

	// Remove nginx configuration
	fmt.Println("🌐 Removing Nginx configuration...")
	domain := "keycloak.local"
	if len(args) > 0 {
		domain = args[0]
	}

	nginxCmds := []string{
		fmt.Sprintf("rm -f /etc/nginx/sites-enabled/%s", domain),
		fmt.Sprintf("rm -f /etc/nginx/sites-available/%s", domain),
	}

	for _, cmd := range nginxCmds {
		conn.RunSudo(cmd, sudoPass)
	}

	// Reload nginx
	conn.RunSudo("systemctl reload nginx", sudoPass)

	// Remove installation directory
	fmt.Println("📁 Removing installation directory...")
	if result := conn.RunSudo(fmt.Sprintf("rm -rf %s", keycloakDir), sudoPass); !result.Success {
		fmt.Printf("⚠️  Failed to remove installation directory: %s\n", result.Stderr)
	}

	// Remove Docker images and volumes
	fmt.Println("🐳 Cleaning up Docker resources...")
	cleanupCmds := []string{
		"docker rmi quay.io/keycloak/keycloak:23.0.0 2>/dev/null || true",
		"docker rmi postgres:15 2>/dev/null || true",
		"docker volume rm keycloak_keycloak_db_data 2>/dev/null || true",
	}

	for _, cmd := range cleanupCmds {
		conn.RunCommand(cmd, plugin.WithHideOutput())
	}

	fmt.Println("✅ Keycloak uninstalled successfully!")
	return nil
}

func (p *Plugin) statusHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("🔍 Checking Keycloak status...")

	keycloakDir := "/opt/keycloak"

	// Check if installation exists
	if result := conn.RunCommand(fmt.Sprintf("test -d %s", keycloakDir), plugin.WithHideOutput()); !result.Success {
		return fmt.Errorf("Keycloak is not installed")
	}

	// Check Docker Compose status
	fmt.Println("\n📦 Service Status:")
	if result := conn.RunCommand(fmt.Sprintf("cd %s && docker-compose ps", keycloakDir), plugin.WithHideOutput()); !result.Success {
		fmt.Printf("❌ Failed to get service status: %s\n", result.Stderr)
	} else {
		fmt.Print(result.Stdout)
	}

	// Check container health
	fmt.Println("\n🏥 Health Status:")
	healthCmd := fmt.Sprintf("cd %s && docker-compose exec -T keycloak curl -f http://localhost:8080/health/ready 2>/dev/null && echo 'Healthy' || echo 'Unhealthy'", keycloakDir)
	if result := conn.RunCommand(healthCmd, plugin.WithHideOutput()); result.Success {
		if strings.Contains(result.Stdout, "Healthy") {
			fmt.Println("🟢 Keycloak is healthy and ready")
		} else {
			fmt.Println("🟡 Keycloak is running but not ready")
		}
	} else {
		fmt.Println("🔴 Keycloak is not responding")
	}

	// Show access URLs
	fmt.Println("\n🌐 Access Information:")
	if result := conn.RunCommand("hostname -f", plugin.WithHideOutput()); result.Success {
		hostname := strings.TrimSpace(result.Stdout)
		fmt.Printf("Admin Console: http://%s/admin\n", hostname)
		fmt.Printf("Base URL: http://%s\n", hostname)
	}

	// Show credentials location
	credentialsFile := fmt.Sprintf("%s/credentials.txt", keycloakDir)
	if result := conn.RunCommand(fmt.Sprintf("test -f %s && echo 'Found' || echo 'Not found'", credentialsFile), plugin.WithHideOutput()); result.Success {
		if strings.Contains(result.Stdout, "Found") {
			fmt.Printf("🔑 Credentials: %s\n", credentialsFile)
		} else {
			fmt.Println("⚠️  Credentials file not found")
		}
	}

	// Show resource usage
	fmt.Println("\n📊 Resource Usage:")
	if result := conn.RunCommand(fmt.Sprintf("cd %s && docker stats --no-stream --format 'table {{.Container}}\\t{{.CPUPerc}}\\t{{.MemUsage}}'", keycloakDir), plugin.WithHideOutput()); result.Success {
		fmt.Print(result.Stdout)
	}

	return nil
}

func (p *Plugin) logsHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("📜 Streaming Keycloak logs (Ctrl+C to stop)...")

	keycloakDir := "/opt/keycloak"

	service := "keycloak"
	if len(args) > 0 {
		service = args[0]
	}

	validServices := []string{"keycloak", "keycloak-db"}
	valid := false
	for _, s := range validServices {
		if service == s {
			valid = true
			break
		}
	}

	if !valid {
		return fmt.Errorf("invalid service: %s. Valid services: %s", service, strings.Join(validServices, ", "))
	}

	cmd := fmt.Sprintf("cd %s && docker-compose logs -f %s", keycloakDir, service)
	return conn.RunInteractive(cmd)
}

func (p *Plugin) realmHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	if len(args) < 1 {
		return fmt.Errorf("usage: realm <create|list|delete> [realm-name]")
	}

	action := args[0]
	keycloakDir := "/opt/keycloak"

	switch action {
	case "list":
		fmt.Println("📋 Listing Keycloak realms...")
		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get realms --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir)
		return conn.RunInteractive(cmd)

	case "create":
		if len(args) < 2 {
			return fmt.Errorf("usage: realm create <realm-name>")
		}
		realmName := args[1]
		fmt.Printf("🏗️  Creating realm: %s\n", realmName)
		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create realms -s realm=%s -s enabled=true --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir, realmName)
		return conn.RunInteractive(cmd)

	case "delete":
		if len(args) < 2 {
			return fmt.Errorf("usage: realm delete <realm-name>")
		}
		realmName := args[1]
		fmt.Printf("🗑️  Deleting realm: %s\n", realmName)
		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh delete realms/%s --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir, realmName)
		return conn.RunInteractive(cmd)

	default:
		return fmt.Errorf("unknown action: %s. Use: create, list, delete", action)
	}
}

func (p *Plugin) userHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	if len(args) < 1 {
		return fmt.Errorf("usage: user <create|list|reset-password> [username] [realm]")
	}

	action := args[0]
	keycloakDir := "/opt/keycloak"
	realm := "master"
	if len(args) >= 3 {
		realm = args[2]
	}

	switch action {
	case "list":
		fmt.Printf("📋 Listing users in realm '%s'...\n", realm)
		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get users -r %s --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir, realm)
		return conn.RunInteractive(cmd)

	case "create":
		if len(args) < 2 {
			return fmt.Errorf("usage: user create <username> [realm]")
		}
		username := args[1]
		fmt.Printf("👤 Creating user: %s\n", username)

		// Generate random password
		password := generateRandomPassword(12)

		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create users -r %s -s username=%s -s enabled=true -s credentials=[{"type":"password","value":"%s","temporary":false}] --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir, realm, username, password)

		if err := conn.RunInteractive(cmd); err != nil {
			return err
		}

		fmt.Printf("✅ User '%s' created with password: %s\n", username, password)
		return nil

	case "reset-password":
		if len(args) < 2 {
			return fmt.Errorf("usage: user reset-password <username> [realm]")
		}
		username := args[1]
		fmt.Printf("🔄 Resetting password for user: %s\n", username)

		password := generateRandomPassword(12)
		fmt.Printf("New password: %s\n", password)

		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh set-password -r %s -u %s --new-password="%s" --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir, realm, username, password)
		return conn.RunInteractive(cmd)

	default:
		return fmt.Errorf("unknown action: %s. Use: create, list, reset-password", action)
	}
}

func (p *Plugin) clientHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	if len(args) < 1 {
		return fmt.Errorf("usage: client <create|list> <client-name> [realm]")
	}

	action := args[0]
	keycloakDir := "/opt/keycloak"
	realm := "master"
	if len(args) >= 3 {
		realm = args[2]
	}

	switch action {
	case "list":
		fmt.Printf("📋 Listing clients in realm '%s'...\n", realm)
		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get clients -r %s --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir, realm)
		return conn.RunInteractive(cmd)

	case "create":
		if len(args) < 2 {
			return fmt.Errorf("usage: client create <client-name> [realm]")
		}
		clientName := args[1]
		fmt.Printf("🔗 Creating client: %s\n", clientName)
		cmd := fmt.Sprintf(`cd %s && docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create clients -r %s -s clientId=%s -s enabled=true -s publicClient=true -s redirectUris=["*"] --config /opt/keycloak/conf/keycloak-cli.properties`, keycloakDir, realm, clientName)
		return conn.RunInteractive(cmd)

	default:
		return fmt.Errorf("unknown action: %s. Use: create, list", action)
	}
}

func (p *Plugin) sslHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	sudoPass := getSudoPass(flags)

	domain := "keycloak.local"
	if len(args) > 0 {
		domain = args[0]
	}

	fmt.Printf("🔒 Configuring SSL for %s...\n", domain)

	// Install certbot and nginx plugin
	fmt.Println("📦 Installing Certbot...")
	pkgMgr := getPackageManager(conn)
	updateCmd, _ := pkgMgr.Update()
	if result := conn.RunSudo(updateCmd, sudoPass); !result.Success {
		fmt.Printf("⚠️  Failed to update packages: %s\n", result.Stderr)
	}
	installCmd, err := pkgMgr.Install("certbot", "python3-certbot-nginx")
	if err != nil {
		fmt.Printf("⚠️  Failed to install certbot: %v\n", err)
	} else {
		if result := conn.RunSudo(installCmd, sudoPass); !result.Success {
			fmt.Printf("⚠️  Failed to install certbot: %s\n", result.Stderr)
		}
	}

	// Obtain SSL certificate
	fmt.Printf("🔐 Obtaining SSL certificate for %s...\n", domain)
	cmd := fmt.Sprintf("certbot --nginx -d %s --non-interactive --agree-tos --email admin@%s", domain, domain)

	if result := conn.RunSudo(cmd, sudoPass); !result.Success {
		return fmt.Errorf("failed to obtain SSL certificate: %s", result.Stderr)
	}

	// Update nginx config for SSL
	fmt.Println("🔧 Updating Nginx configuration for SSL...")
	sslConfig := fmt.Sprintf(`server {
    listen 80;
    server_name %s;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name %s;

    ssl_certificate /etc/letsencrypt/live/%s/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/%s/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket support
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}`, domain, domain, domain, domain)

	nginxConfigPath := fmt.Sprintf("/etc/nginx/sites-available/%s", domain)

	if err := conn.WriteFile(sslConfig, fmt.Sprintf("/tmp/%s-ssl.conf", domain)); err != nil {
		return fmt.Errorf("failed to write SSL config: %v", err)
	}

	if result := conn.RunSudo(fmt.Sprintf("mv /tmp/%s-ssl.conf %s", domain, nginxConfigPath), sudoPass); !result.Success {
		return fmt.Errorf("failed to update nginx config: %s", result.Stderr)
	}

	// Test and reload nginx
	if result := conn.RunSudo("nginx -t", sudoPass); !result.Success {
		return fmt.Errorf("nginx config test failed: %s", result.Stderr)
	}

	if result := conn.RunSudo("systemctl reload nginx", sudoPass); !result.Success {
		return fmt.Errorf("failed to reload nginx: %s", result.Stderr)
	}

	// Update Keycloak hostname configuration
	fmt.Println("🔧 Updating Keycloak configuration...")
	keycloakDir := "/opt/keycloak"

	// Update docker-compose.yml to enable HTTPS
	updateComposeCmd := fmt.Sprintf("cd %s && sed -i +e 's/KC_HOSTNAME_STRICT_HTTPS: false/KC_HOSTNAME_STRICT_HTTPS: true/' docker-compose.yml", keycloakDir)
	conn.RunCommand(updateComposeCmd, plugin.WithHideOutput())

	// Restart Keycloak to apply changes
	fmt.Println("🔄 Restarting Keycloak to apply SSL configuration...")
	if result := conn.RunCommand(fmt.Sprintf("cd %s && docker-compose restart keycloak", keycloakDir), plugin.WithHideOutput()); !result.Success {
		fmt.Printf("⚠️  Failed to restart Keycloak: %s\n", result.Stderr)
	}

	fmt.Println("✅ SSL configured successfully!")
	fmt.Printf("🌐 HTTPS URL: https://%s\n", domain)
	fmt.Printf("🔑 Admin Console: https://%s/admin\n", domain)

	return nil
}

func (p *Plugin) backupHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	sudoPass := getSudoPass(flags)

	fmt.Println("💾 Creating Keycloak backup...")

	keycloakDir := "/opt/keycloak"
	backupDir := "/var/backups/keycloak"

	// Create backup directory
	fmt.Printf("📁 Creating backup directory: %s\n", backupDir)
	if result := conn.RunSudo(fmt.Sprintf("mkdir -p %s", backupDir), sudoPass); !result.Success {
		return fmt.Errorf("failed to create backup directory: %s", result.Stderr)
	}

	// Generate timestamp
	result := conn.RunCommand("date '+%Y%m%d_%H%M%S'", plugin.WithHideOutput())
	if !result.Success {
		return fmt.Errorf("failed to generate timestamp")
	}
	timestamp := strings.TrimSpace(result.Stdout)

	// Create backup file
	backupFile := fmt.Sprintf("%s/keycloak_backup_%s.tar.gz", backupDir, timestamp)
	fmt.Printf("💾 Creating backup: %s\n", backupFile)

	// Backup directory
	cmd := fmt.Sprintf("tar -czf %s %s", backupFile, keycloakDir)
	if result := conn.RunSudo(cmd, sudoPass); !result.Success {
		return fmt.Errorf("failed to create backup: %s", result.Stderr)
	}

	// Set permissions
	if result := conn.RunSudo(fmt.Sprintf("chmod 600 %s", backupFile), sudoPass); !result.Success {
		fmt.Printf("⚠️  Failed to set backup permissions\n")
	}

	// Show backup size
	if result := conn.RunCommand(fmt.Sprintf("sudo ls -lh %s | awk '{print $5}'", backupFile), plugin.WithHideOutput()); result.Success {
		fmt.Printf("📊 Backup size: %s\n", strings.TrimSpace(result.Stdout))
	}

	fmt.Println("✅ Keycloak backup completed successfully!")
	fmt.Printf("📁 Backup file: %s\n", backupFile)

	return nil
}

func (p *Plugin) restoreHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	sudoPass := getSudoPass(flags)

	if len(args) < 1 {
		return fmt.Errorf("usage: restore <backup-file>")
	}

	backupFile := args[0]
	fmt.Printf("🔄 Restoring Keycloak from backup: %s\n", backupFile)

	// Check if backup file exists
	if result := conn.RunCommand(fmt.Sprintf("test -f %s", backupFile), plugin.WithHideOutput()); !result.Success {
		return fmt.Errorf("backup file not found: %s", backupFile)
	}

	// Stop current services
	fmt.Println("🛑 Stopping current Keycloak services...")
	keycloakDir := "/opt/keycloak"
	if result := conn.RunCommand(fmt.Sprintf("cd %s && docker-compose down", keycloakDir), plugin.WithHideOutput()); !result.Success {
		fmt.Printf("⚠️  Failed to stop services: %s\n", result.Stderr)
	}

	// Remove current installation
	fmt.Println("🗑️  Removing current installation...")
	if result := conn.RunSudo("rm -rf /opt/keycloak", sudoPass); !result.Success {
		return fmt.Errorf("failed to remove current installation: %s", result.Stderr)
	}

	// Extract backup
	fmt.Println("📂 Extracting backup...")
	cmd := fmt.Sprintf("cd /opt && tar -xzf %s", backupFile)
	if result := conn.RunSudo(cmd, sudoPass); !result.Success {
		return fmt.Errorf("failed to extract backup: %s", result.Stderr)
	}

	// Start services
	fmt.Println("🚀 Starting restored services...")
	if result := conn.RunCommand(fmt.Sprintf("cd %s && docker-compose up -d", keycloakDir), plugin.WithHideOutput()); !result.Success {
		return fmt.Errorf("failed to start restored services: %s", result.Stderr)
	}

	// Wait for services to be ready
	fmt.Println("⏳ Waiting for services to start...")
	if err := p.waitForKeycloakReady(conn); err != nil {
		fmt.Printf("⚠️  Services may need additional time: %v\n", err)
	}

	fmt.Println("✅ Keycloak restore completed successfully!")
	return nil
}

func (p *Plugin) configureHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	configureScript := `
echo "🔧 Keycloak Configuration Menu"
echo "================================"
echo ""
echo "1. View current configuration"
echo "2. Update admin password"
echo "3. Change domain"
echo "4. View service status"
echo "5. Show access URLs"
echo "0. Exit"
echo ""
read -p "Select option (0-5): " choice

case $choice in
    1)
        echo "📋 Current Configuration:"
        if [ -f /opt/keycloak/docker-compose.yml ]; then
            echo "Installation Directory: /opt/keycloak"
            echo "Services:"
            cd /opt/keycloak && docker-compose ps
        else
            echo "Keycloak is not installed"
        fi
        ;;
    2)
        echo "🔑 Updating admin password..."
        read -s -p "Enter new admin password: " new_password
        echo
        cd /opt/keycloak
        docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update users/$(docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get users -r master -q username=admin --fields id --config /opt/keycloak/conf/keycloak-cli.properties | grep -o '"id":"[^"]*"' | cut -d'"' -f4) -r master -s 'credentials=[{"type":"password","value":"'"$new_password"'","temporary":false}]' --config /opt/keycloak/conf/keycloak-cli.properties
        echo "✅ Admin password updated"
        ;;
    3)
        echo "🌐 Updating domain configuration..."
        read -p "Enter new domain: " new_domain
        # This would require updating docker-compose.yml and nginx config
        echo "⚠️  Domain update requires manual reconfiguration"
        ;;
    4)
        echo "🔍 Service Status:"
        cd /opt/keycloak && docker-compose ps
        ;;
    5)
        echo "🌐 Access URLs:"
        hostname=$(hostname -f)
        echo "Base URL: http://$hostname"
        echo "Admin Console: http://$hostname/admin"
        ;;
    0)
        echo "👋 Exiting..."
        ;;
    *)
        echo "❌ Invalid option"
        ;;
esac
`

	return conn.RunInteractive(configureScript)
}

func (p *Plugin) waitForKeycloakReady(conn plugin.Connection) error {
	maxAttempts := 60
	for i := 0; i < maxAttempts; i++ {
		healthCmd := `cd /opt/keycloak && docker-compose exec -T keycloak curl -f http://localhost:8080/health/ready 2>/dev/null && echo "ready" || echo "not_ready"`

		if result := conn.RunCommand(healthCmd, plugin.WithHideOutput()); result.Success {
			if strings.Contains(result.Stdout, "ready") {
				return nil
			}
		}

		fmt.Print(".")
		time.Sleep(2 * time.Second)
	}

	return fmt.Errorf("timeout waiting for Keycloak to be ready")
}

func generateRandomPassword(length int) string {
	const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	rand.Seed(time.Now().UnixNano())

	password := make([]byte, length)
	for i := range password {
		password[i] = charset[rand.Intn(len(charset))]
	}

	return string(password)
}
