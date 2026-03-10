package kong

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/wasilwamark/mellow/pkg/plugin"
)

type Plugin struct{}

func (p *Plugin) Name() string {
	return "kong"
}

func (p *Plugin) Description() string {
	return "Kong API Gateway management (Docker-based)"
}

func (p *Plugin) Version() string {
	return "1.0.0"
}

func (p *Plugin) Author() string {
	return "Mellow Team"
}

func (p *Plugin) Initialize(config map[string]interface{}) error {
	return nil
}

func (p *Plugin) Validate() error {
	return nil
}

func (p *Plugin) Start(ctx context.Context) error {
	return nil
}

func (p *Plugin) Stop(ctx context.Context) error {
	return nil
}

func (p *Plugin) GetRootCommand() *cobra.Command {
	return nil
}

func (p *Plugin) Dependencies() []plugin.Dependency {
	return []plugin.Dependency{
		{
			Name:     "docker",
			Version:  ">=1.0.0",
			Optional: false,
		},
	}
}

func (p *Plugin) Compatibility() plugin.Compatibility {
	return plugin.Compatibility{
		MinVPSInitVersion: "1.0.0",
		GoVersion:         "1.19",
		Platforms:         []string{"linux/amd64", "linux/arm64"},
		Tags:              []string{"api-gateway", "microservices", "kong", "docker"},
	}
}

func (p *Plugin) GetMetadata() plugin.PluginMetadata {
	return plugin.PluginMetadata{
		Name:        "kong",
		Description: "Kong API Gateway management (Docker-based)",
		Version:     "1.0.0",
		Author:      "Mellow Team",
		License:     "MIT",
		Repository:  "github.com/wasilwamark/mellow-plugins/kong",
		Tags:        []string{"api-gateway", "kong", "docker", "microservices"},
		Validated:   true,
		TrustLevel:  "official",
		BuildInfo: plugin.BuildInfo{
			GoVersion: "1.21",
		},
	}
}

func (p *Plugin) GetCommands() []plugin.Command {
	return []plugin.Command{
		{
			Name:        "install",
			Description: "Install Kong Gateway with Docker Compose",
			Handler:     p.installHandler,
		},
		{
			Name:        "start",
			Description: "Start Kong services",
			Handler:     p.startHandler,
		},
		{
			Name:        "stop",
			Description: "Stop Kong services",
			Handler:     p.stopHandler,
		},
		{
			Name:        "restart",
			Description: "Restart Kong services",
			Handler:     p.restartHandler,
		},
		{
			Name:        "status",
			Description: "Check Kong services status",
			Handler:     p.statusHandler,
		},
		{
			Name:        "logs",
			Description: "Stream Kong logs",
			Handler:     p.logsHandler,
		},
		{
			Name:        "uninstall",
			Description: "Remove Kong containers and volumes",
			Handler:     p.uninstallHandler,
		},
		{
			Name:        "add-service",
			Description: "Add a service to Kong",
			Handler:     p.addServiceHandler,
		},
		{
			Name:        "list-services",
			Description: "List all Kong services",
			Handler:     p.listServicesHandler,
		},
		{
			Name:        "remove-service",
			Description: "Remove a service from Kong",
			Handler:     p.removeServiceHandler,
		},
		{
			Name:        "add-route",
			Description: "Add a route to a service",
			Handler:     p.addRouteHandler,
		},
		{
			Name:        "list-routes",
			Description: "List all routes",
			Handler:     p.listRoutesHandler,
		},
		{
			Name:        "enable-plugin",
			Description: "Enable a plugin on a service/route",
			Handler:     p.enablePluginHandler,
		},
		{
			Name:        "list-plugins",
			Description: "List enabled plugins",
			Handler:     p.listPluginsHandler,
		},
		{
			Name:        "config",
			Description: "Show Kong configuration",
			Handler:     p.configHandler,
		},
		{
			Name:        "health",
			Description: "Check Kong health",
			Handler:     p.healthHandler,
		},
		{
			Name:        "shell",
			Description: "Open shell in Kong container",
			Handler:     p.shellHandler,
		},
	}
}

// KongService represents a Kong service configuration
type KongService struct {
	Name     string `json:"name"`
	Host     string `json:"host"`
	Port     int    `json:"port"`
	Protocol string `json:"protocol"`
	Path     string `json:"path,omitempty"`
}

// KongRoute represents a Kong route configuration
type KongRoute struct {
	Name       string   `json:"name"`
	ServiceID  string   `json:"service"`
	Paths      []string `json:"paths,omitempty"`
	Methods    []string `json:"methods,omitempty"`
	Hosts      []string `json:"hosts,omitempty"`
	Protocols  []string `json:"protocols,omitempty"`
	StripPath  bool     `json:"strip_path,omitempty"`
	PreserveHost bool   `json:"preserve_host,omitempty"`
}

// KongPlugin represents a Kong plugin configuration
type KongPlugin struct {
	Name      string                 `json:"name"`
	ServiceID string                 `json:"service,omitempty"`
	RouteID   string                 `json:"route,omitempty"`
	Enabled   bool                   `json:"enabled"`
	Config    map[string]interface{} `json:"config,omitempty"`
}

const (
	kongDir          = "/opt/kong"
	kongComposeFile  = kongDir + "/docker-compose.yml"
	kongConfFile     = kongDir + "/kong.conf"
	kongAdminURL     = "http://localhost:8001"
	kongNetworkName  = "kong-net"
	kongDefaultDBPort = "15432" // Use unique port to avoid conflicts
)

// Basic Kong configuration file
const kongConfig = `
# Kong Configuration
# Generated by Mellow

proxy_access_log = /dev/stdout
admin_access_log = /dev/stdout
proxy_error_log = /dev/stderr
admin_error_log = /dev/stderr

admin_listen = 0.0.0.0:8001
cluster_listen = 0.0.0.0:8005

database = postgres
pg_host = kong-db
pg_database = kong
pg_user = kong
pg_password = kong_password_change_me

# Performance tuning
nginx_worker_processes = auto
nginx_worker_connections = 1024

# Plugin bundles
# pluginserver_names =
`

func (p *Plugin) installHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	pass := getPassword(flags)

	fmt.Println("🦍 Setting up Kong Gateway with Docker...")

	// Check if Docker is installed
	fmt.Println("🐳 Checking Docker installation...")
	dockerCheck := conn.RunCommand("docker --version", plugin.WithHideOutput())
	if !dockerCheck.Success {
		return fmt.Errorf("Docker is not installed. Please install Docker first: mellow <server> docker install")
	}
	fmt.Printf("✅ Docker detected: %s\n", strings.TrimSpace(dockerCheck.Stdout))

	// Check if docker compose is available
	composeCheck := conn.RunCommand("docker compose version", plugin.WithHideOutput())
	if !composeCheck.Success {
		return fmt.Errorf("Docker Compose is not available. Please ensure Docker Compose is installed")
	}
	fmt.Printf("✅ Docker Compose detected: %s\n", strings.TrimSpace(composeCheck.Stdout))

	// Check for port conflicts and determine PostgreSQL port
	dbPort := kongDefaultDBPort
	fmt.Println("\n🔍 Checking for port conflicts...")

	// Check if default PostgreSQL port 5432 is in use
	portCheck := conn.RunCommand("docker ps --format '{{.Ports}}' | grep -q ':5432->' && echo 'IN_USE' || echo 'AVAILABLE'", plugin.WithHideOutput())
	if portCheck.Success && strings.Contains(portCheck.Stdout, "AVAILABLE") {
		// Port 5432 is available, ask user
		fmt.Println("ℹ️  PostgreSQL default port 5432 is available.")
		fmt.Println("   Would you like to use port 5432 or the alternative port 15432?")
		fmt.Println("   Using 15432 is recommended to avoid conflicts with other databases.")
		// For now, we'll use 15432 by default to avoid conflicts
		dbPort = "15432"
		fmt.Println("   Using port 15432 for Kong database.")
	} else {
		fmt.Println("ℹ️  PostgreSQL default port 5432 is already in use.")
		fmt.Printf("   Using port %s for Kong database.\n", dbPort)
	}

	// Create Kong directory
	fmt.Printf("📁 Creating Kong directory at %s...\n", kongDir)
	if result := conn.RunSudo(fmt.Sprintf("mkdir -p %s", kongDir), pass); !result.Success {
		return fmt.Errorf("failed to create Kong directory: %w", result.GetError())
	}

	// Write docker-compose.yml to temp location first
	fmt.Println("📝 Writing docker-compose.yml...")
	tmpComposeFile := "/tmp/docker-compose.yml"

	// Generate docker-compose.yml with the correct port
	composeContent := p.generateDockerCompose(dbPort)
	if err := conn.WriteFile(composeContent, tmpComposeFile); err != nil {
		return fmt.Errorf("failed to write docker-compose.yml: %w", err)
	}
	if result := conn.RunSudo(fmt.Sprintf("mv %s %s", tmpComposeFile, kongComposeFile), pass); !result.Success {
		return fmt.Errorf("failed to move docker-compose.yml: %w", result.GetError())
	}

	// Write kong.conf to temp location first
	fmt.Println("📝 Writing kong.conf...")
	tmpConfFile := "/tmp/kong.conf"
	if err := conn.WriteFile(kongConfig, tmpConfFile); err != nil {
		return fmt.Errorf("failed to write kong.conf: %w", err)
	}
	if result := conn.RunSudo(fmt.Sprintf("mv %s %s", tmpConfFile, kongConfFile), pass); !result.Success {
		return fmt.Errorf("failed to move kong.conf: %w", result.GetError())
	}

	// Create network
	fmt.Println("🌐 Creating Docker network...")
	conn.RunCommand(fmt.Sprintf("docker network create %s 2>/dev/null || echo 'Network already exists'", kongNetworkName), plugin.WithHideOutput())

	// Start services
	fmt.Println("🚀 Starting Kong services...")
	if result := conn.RunSudo(fmt.Sprintf("docker compose -f %s up -d", kongComposeFile), pass); !result.Success {
		return fmt.Errorf("failed to start Kong services: %w", result.GetError())
	}

	// Wait for Kong to be healthy
	fmt.Println("⏳ Waiting for Kong to start...")
	if err := p.waitForKong(conn); err != nil {
		return fmt.Errorf("Kong health check failed: %w", err)
	}

	fmt.Println("\n✅ Kong Gateway installed successfully!")
	fmt.Println("\n📋 Access Information:")
	fmt.Println("   🌐 Proxy HTTP:  http://localhost:8000")
	fmt.Println("   🔒 Proxy HTTPS: http://localhost:8443")
	fmt.Println("   ⚙️  Admin API:   http://localhost:8001")
	fmt.Println("   📊 Manager UI:  http://localhost:8002")
	fmt.Printf("   🗄️  PostgreSQL: localhost:%s\n", dbPort)
	fmt.Println("\n🔐 Default Credentials:")
	fmt.Println("   PostgreSQL User: kong")
	fmt.Println("   PostgreSQL Password: kong_password_change_me")
	fmt.Printf("   PostgreSQL Port: %s\n", dbPort)
	fmt.Println("\n⚠️  IMPORTANT: Change the PostgreSQL password in production!")
	fmt.Println("\n📚 Next Steps:")
	fmt.Println("   - Add a service:  mellow <server> kong add-service <name> --host <host> --port <port>")
	fmt.Println("   - Add a route:    mellow <server> kong add-route <route-name> --service <service-id> --path <path>")
	fmt.Println("   - View logs:      mellow <server> kong logs")
	fmt.Println("   - Check health:   mellow <server> kong health")
	fmt.Println("   - View status:    mellow <server> kong status")

	return nil
}

func (p *Plugin) waitForKong(conn plugin.Connection) error {
	maxAttempts := 30
	for i := 0; i < maxAttempts; i++ {
		fmt.Print(".")

		// Try multiple health check approaches for Kong Gateway 3.6
		healthChecks := []string{
			// Check if Admin API is responding (200 status)
			"curl -s -o /dev/null -w '%{http_code}' http://localhost:8001/ 2>/dev/null | grep -q '200'",
			// Check for Kong root endpoint
			"curl -s http://localhost:8001/ 2>/dev/null | grep -q 'hostname'",
			// Check container health status
			"docker inspect kong-gateway --format='{{.State.Health.Status}}' 2>/dev/null | grep -q 'healthy'",
		}

		for _, check := range healthChecks {
			result := conn.RunCommand(check, plugin.WithHideOutput())
			if result.Success {
				fmt.Println()
				return nil
			}
		}

		time.Sleep(2 * time.Second)
	}
	return fmt.Errorf("timeout waiting for Kong to become healthy")
}

func (p *Plugin) startHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("🚀 Starting Kong services...")
	pass := getPassword(flags)

	if result := conn.RunSudo(fmt.Sprintf("docker compose -f %s up -d", kongComposeFile), pass); !result.Success {
		return fmt.Errorf("failed to start Kong: %w", result.GetError())
	}

	fmt.Println("✅ Kong services started!")
	return p.statusHandler(ctx, conn, args, flags)
}

func (p *Plugin) stopHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("⏹️  Stopping Kong services...")
	pass := getPassword(flags)

	if result := conn.RunSudo(fmt.Sprintf("docker compose -f %s stop", kongComposeFile), pass); !result.Success {
		return fmt.Errorf("failed to stop Kong: %w", result.GetError())
	}

	fmt.Println("✅ Kong services stopped!")
	return nil
}

func (p *Plugin) restartHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("🔄 Restarting Kong services...")
	pass := getPassword(flags)

	if result := conn.RunSudo(fmt.Sprintf("docker compose -f %s restart", kongComposeFile), pass); !result.Success {
		return fmt.Errorf("failed to restart Kong: %w", result.GetError())
	}

	fmt.Println("✅ Kong services restarted!")
	return p.statusHandler(ctx, conn, args, flags)
}

func (p *Plugin) statusHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("📊 Kong Services Status:")
	fmt.Println("========================")

	// Check Docker containers
	result := conn.RunCommand(fmt.Sprintf("docker compose -f %s ps", kongComposeFile), plugin.WithHideOutput())
	if result.Success {
		fmt.Print(result.Stdout)
	} else {
		fmt.Println("❌ Failed to get Kong status")
	}

	// Show port mappings
	fmt.Println("\n🔌 Port Mappings:")
	portResult := conn.RunCommand(fmt.Sprintf("docker compose -f %s ps --format 'table {{.Name}}\t{{.Ports}}'", kongComposeFile), plugin.WithHideOutput())
	if portResult.Success {
		fmt.Print(portResult.Stdout)
	}

	// Check Kong health
	fmt.Println("\n🏥 Kong Health:")
	healthResult := conn.RunCommand("curl -s http://localhost:8001/health 2>/dev/null || echo 'Kong not responding'", plugin.WithHideOutput())
	if healthResult.Success && healthResult.Stdout != "" {
		fmt.Print(healthResult.Stdout)
	} else {
		fmt.Println("❌ Kong health check failed")
	}

	return nil
}

func (p *Plugin) logsHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	service := "kong"
	if len(args) > 0 {
		service = args[0]
	}

	fmt.Printf("📜 Streaming Kong logs (%s) (Ctrl+C to stop)...\n", service)
	cmd := fmt.Sprintf("docker compose -f %s logs -f %s", kongComposeFile, service)
	return conn.RunInteractive(cmd)
}

func (p *Plugin) uninstallHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("⚠️  This will remove Kong containers and volumes.")
	fmt.Println("   Data will be permanently lost!")

	// For now, proceed with removal (in real CLI, you'd ask for confirmation)
	fmt.Println("🗑️  Removing Kong services...")

	pass := getPassword(flags)

	// Stop and remove containers
	if result := conn.RunSudo(fmt.Sprintf("docker compose -f %s down -v", kongComposeFile), pass); !result.Success {
		return fmt.Errorf("failed to remove Kong: %w", result.GetError())
	}

	// Remove directory
	if result := conn.RunSudo(fmt.Sprintf("rm -rf %s", kongDir), pass); !result.Success {
		fmt.Printf("⚠️  Failed to remove Kong directory: %v\n", result.GetError())
	}

	fmt.Println("✅ Kong removed successfully!")
	return nil
}

func (p *Plugin) addServiceHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	if len(args) < 1 {
		return fmt.Errorf("usage: add-service <name> --host <host> --port <port> [--protocol <protocol>] [--path <path>]")
	}

	serviceName := args[0]
	host := getFlag(flags, "host", "")
	portStr := getFlag(flags, "port", "80")
	protocol := getFlag(flags, "protocol", "http")
	path := getFlag(flags, "path", "")

	if host == "" {
		return fmt.Errorf("--host is required")
	}

	port := 80
	if _, err := fmt.Sscanf(portStr, "%d", &port); err != nil {
		return fmt.Errorf("invalid port: %s", portStr)
	}

	service := KongService{
		Name:     serviceName,
		Host:     host,
		Port:     port,
		Protocol: protocol,
		Path:     path,
	}

	fmt.Printf("📝 Adding service '%s'...\n", serviceName)
	fmt.Printf("   Host: %s:%d\n", host, port)
	fmt.Printf("   Protocol: %s\n", protocol)
	if path != "" {
		fmt.Printf("   Path: %s\n", path)
	}

	serviceJSON, err := json.Marshal(service)
	if err != nil {
		return fmt.Errorf("failed to marshal service: %w", err)
	}

	cmd := fmt.Sprintf("curl -s -X POST http://localhost:8001/services -H 'Content-Type: application/json' -d '%s'", string(serviceJSON))

	result := conn.RunCommand(cmd, plugin.WithHideOutput())
	if !result.Success {
		return fmt.Errorf("failed to add service: %s", result.Stderr)
	}

	fmt.Println("✅ Service added successfully!")

	// Show the created service
	var response map[string]interface{}
	if err := json.Unmarshal([]byte(result.Stdout), &response); err == nil {
		if id, ok := response["id"].(string); ok {
			fmt.Printf("   Service ID: %s\n", id)
		}
	}

	return nil
}

func (p *Plugin) listServicesHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("📋 Kong Services:")
	fmt.Println("=================")

	cmd := "curl -s http://localhost:8001/services"
	result := conn.RunCommand(cmd, plugin.WithHideOutput())

	if !result.Success {
		return fmt.Errorf("failed to list services: %s", result.Stderr)
	}

	var response map[string]interface{}
	if err := json.Unmarshal([]byte(result.Stdout), &response); err != nil {
		return fmt.Errorf("failed to parse response: %w", err)
	}

	data, ok := response["data"].([]interface{})
	if !ok {
		fmt.Println("No services found.")
		return nil
	}

	if len(data) == 0 {
		fmt.Println("No services configured.")
		return nil
	}

	for _, svc := range data {
		service, ok := svc.(map[string]interface{})
		if !ok {
			continue
		}

		name := getString(service, "name")
		id := getString(service, "id")
		host := getString(service, "host")
		port := getInt(service, "port")
		protocol := getString(service, "protocol")

		fmt.Printf("\n🔷 %s\n", name)
		fmt.Printf("   ID: %s\n", id)
		fmt.Printf("   URL: %s://%s:%d\n", protocol, host, port)
	}

	return nil
}

func (p *Plugin) removeServiceHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	if len(args) < 1 {
		return fmt.Errorf("usage: remove-service <service-name-or-id>")
	}

	serviceID := args[0]
	fmt.Printf("🗑️  Removing service '%s'...\n", serviceID)

	// First try to get the service by name to get ID
	cmd := fmt.Sprintf("curl -s http://localhost:8001/services/%s", serviceID)
	result := conn.RunCommand(cmd, plugin.WithHideOutput())

	if result.Success {
		var response map[string]interface{}
		if err := json.Unmarshal([]byte(result.Stdout), &response); err == nil {
			if id, ok := response["id"].(string); ok {
				serviceID = id
			}
		}
	}

	cmd = fmt.Sprintf("curl -s -X DELETE http://localhost:8001/services/%s", serviceID)
	result = conn.RunCommand(cmd, plugin.WithHideOutput())

	if !result.Success {
		return fmt.Errorf("failed to remove service: %s", result.Stderr)
	}

	fmt.Println("✅ Service removed successfully!")
	return nil
}

func (p *Plugin) addRouteHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	if len(args) < 1 {
		return fmt.Errorf("usage: add-route <route-name> --service <service-name-or-id> --path <path> [--methods <methods>]")
	}

	routeName := args[0]
	serviceName := getFlag(flags, "service", "")
	path := getFlag(flags, "path", "/")
	methodsStr := getFlag(flags, "methods", "")

	if serviceName == "" {
		return fmt.Errorf("--service is required")
	}

	// Get service ID
	fmt.Printf("🔍 Looking up service '%s'...\n", serviceName)
	cmd := fmt.Sprintf("curl -s http://localhost:8001/services/%s", serviceName)
	result := conn.RunCommand(cmd, plugin.WithHideOutput())

	if !result.Success {
		return fmt.Errorf("service not found: %s", serviceName)
	}

	var serviceResponse map[string]interface{}
	if err := json.Unmarshal([]byte(result.Stdout), &serviceResponse); err != nil {
		return fmt.Errorf("failed to parse service response: %w", err)
	}

	serviceID, ok := serviceResponse["id"].(string)
	if !ok {
		return fmt.Errorf("failed to get service ID")
	}

	route := KongRoute{
		Name:      routeName,
		ServiceID: serviceID,
		Paths:     []string{path},
		Protocols: []string{"http", "https"},
		StripPath: true,
	}

	if methodsStr != "" {
		route.Methods = strings.Split(methodsStr, ",")
	}

	fmt.Printf("📝 Adding route '%s' to service '%s'...\n", routeName, serviceName)
	fmt.Printf("   Path: %s\n", path)
	if len(route.Methods) > 0 {
		fmt.Printf("   Methods: %v\n", route.Methods)
	}

	routeJSON, err := json.Marshal(route)
	if err != nil {
		return fmt.Errorf("failed to marshal route: %w", err)
	}

	cmd = fmt.Sprintf("curl -s -X POST http://localhost:8001/routes -H 'Content-Type: application/json' -d '%s'", string(routeJSON))

	result = conn.RunCommand(cmd, plugin.WithHideOutput())
	if !result.Success {
		return fmt.Errorf("failed to add route: %s", result.Stderr)
	}

	fmt.Println("✅ Route added successfully!")

	// Show the created route
	var response map[string]interface{}
	if err := json.Unmarshal([]byte(result.Stdout), &response); err == nil {
		if id, ok := response["id"].(string); ok {
			fmt.Printf("   Route ID: %s\n", id)
		}
	}

	fmt.Printf("\n🌐 Access your route at: http://localhost:8000%s\n", path)

	return nil
}

func (p *Plugin) listRoutesHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("🛣️  Kong Routes:")
	fmt.Println("================")

	cmd := "curl -s http://localhost:8001/routes"
	result := conn.RunCommand(cmd, plugin.WithHideOutput())

	if !result.Success {
		return fmt.Errorf("failed to list routes: %s", result.Stderr)
	}

	var response map[string]interface{}
	if err := json.Unmarshal([]byte(result.Stdout), &response); err != nil {
		return fmt.Errorf("failed to parse response: %w", err)
	}

	data, ok := response["data"].([]interface{})
	if !ok || len(data) == 0 {
		fmt.Println("No routes configured.")
		return nil
	}

	for _, route := range data {
		r, ok := route.(map[string]interface{})
		if !ok {
			continue
		}

		name := getString(r, "name")
		id := getString(r, "id")

		// Get paths
		var paths []string
		if pathsData, ok := r["paths"].([]interface{}); ok {
			for _, p := range pathsData {
				if pathStr, ok := p.(string); ok {
					paths = append(paths, pathStr)
				}
			}
		}

		fmt.Printf("\n🔷 %s\n", name)
		fmt.Printf("   ID: %s\n", id)
		if len(paths) > 0 {
			fmt.Printf("   Paths: %v\n", paths)
			fmt.Printf("   Access: http://localhost:8000%s\n", paths[0])
		}
	}

	return nil
}

func (p *Plugin) enablePluginHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	if len(args) < 1 {
		return fmt.Errorf("usage: enable-plugin <plugin-name> --service <service-name-or-id> | --route <route-name-or-id>")
	}

	pluginName := args[0]
	serviceName := getFlag(flags, "service", "")
	routeName := getFlag(flags, "route", "")

	if serviceName == "" && routeName == "" {
		return fmt.Errorf("either --service or --route is required")
	}

	pluginConfig := KongPlugin{
		Name:    pluginName,
		Enabled: true,
		Config:  make(map[string]interface{}),
	}

	if serviceName != "" {
		pluginConfig.ServiceID = serviceName
		fmt.Printf("🔌 Enabling plugin '%s' on service '%s'...\n", pluginName, serviceName)
	} else {
		pluginConfig.RouteID = routeName
		fmt.Printf("🔌 Enabling plugin '%s' on route '%s'...\n", pluginName, routeName)
	}

	pluginJSON, err := json.Marshal(pluginConfig)
	if err != nil {
		return fmt.Errorf("failed to marshal plugin: %w", err)
	}

	cmd := fmt.Sprintf("curl -s -X POST http://localhost:8001/plugins -H 'Content-Type: application/json' -d '%s'", string(pluginJSON))

	result := conn.RunCommand(cmd, plugin.WithHideOutput())
	if !result.Success {
		return fmt.Errorf("failed to enable plugin: %s", result.Stderr)
	}

	fmt.Println("✅ Plugin enabled successfully!")
	return nil
}

func (p *Plugin) listPluginsHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("🔌 Enabled Plugins:")
	fmt.Println("===================")

	cmd := "curl -s http://localhost:8001/plugins"
	result := conn.RunCommand(cmd, plugin.WithHideOutput())

	if !result.Success {
		return fmt.Errorf("failed to list plugins: %s", result.Stderr)
	}

	var response map[string]interface{}
	if err := json.Unmarshal([]byte(result.Stdout), &response); err != nil {
		return fmt.Errorf("failed to parse response: %w", err)
	}

	data, ok := response["data"].([]interface{})
	if !ok || len(data) == 0 {
		fmt.Println("No plugins enabled.")
		return nil
	}

	for _, plugin := range data {
		p, ok := plugin.(map[string]interface{})
		if !ok {
			continue
		}

		name := getString(p, "name")
		id := getString(p, "id")
		enabled := getBool(p, "enabled")

		fmt.Printf("\n🔷 %s\n", name)
		fmt.Printf("   ID: %s\n", id)
		fmt.Printf("   Enabled: %v\n", enabled)
	}

	return nil
}

func (p *Plugin) configHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("⚙️  Kong Configuration:")
	fmt.Println("======================")

	cmd := "curl -s http://localhost:8001/"
	result := conn.RunCommand(cmd, plugin.WithHideOutput())

	if !result.Success {
		return fmt.Errorf("failed to get Kong config: %s", result.Stderr)
	}

	var config map[string]interface{}
	if err := json.Unmarshal([]byte(result.Stdout), &config); err != nil {
		return fmt.Errorf("failed to parse config: %w", err)
	}

	// Display key configuration
	if hostname, ok := config["hostname"].(string); ok {
		fmt.Printf("Hostname: %s\n", hostname)
	}

	if version, ok := config["version"].(string); ok {
		fmt.Printf("Version: %s\n", version)
	}

	if tagline, ok := config["tagline"].(string); ok {
		fmt.Printf("Tagline: %s\n", tagline)
	}

	fmt.Println("\n📊 Available Endpoints:")
	fmt.Println("   Services:  /services")
	fmt.Println("   Routes:    /routes")
	fmt.Println("   Plugins:   /plugins")
	fmt.Println("   Consumers: /consumers")
	fmt.Println("   Certificates: /certificates")
	fmt.Println("   Upstreams: /upstreams")
	fmt.Println("   Targets:   /targets")

	return nil
}

func (p *Plugin) healthHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("🏥 Kong Health Check:")
	fmt.Println("====================")

	// Check Admin API
	fmt.Println("\n⚙️  Admin API:")
	cmd := "curl -s -o /dev/null -w '%{http_code}' http://localhost:8001/ 2>/dev/null"
	result := conn.RunCommand(cmd, plugin.WithHideOutput())
	if result.Success && strings.Contains(result.Stdout, "200") {
		fmt.Println("✅ Admin API is accessible (HTTP 200)")
	} else {
		fmt.Printf("❌ Admin API not accessible (HTTP %s)\n", strings.TrimSpace(result.Stdout))
	}

	// Check Kong root endpoint
	fmt.Println("\n🦍 Kong Gateway:")
	cmd = "curl -s http://localhost:8001/ 2>/dev/null | head -10"
	result = conn.RunCommand(cmd, plugin.WithHideOutput())
	if result.Success && result.Stdout != "" {
		if strings.Contains(result.Stdout, "hostname") {
			fmt.Println("✅ Kong Gateway is responding")
			// Try to extract and show version info
			if strings.Contains(result.Stdout, "version") {
				lines := strings.Split(result.Stdout, "\n")
				for _, line := range lines {
					if strings.Contains(line, "tagline") || strings.Contains(line, "version") {
						fmt.Printf("   %s\n", strings.TrimSpace(line))
					}
				}
			}
		}
	} else {
		fmt.Println("❌ Kong Gateway not responding")
	}

	// Check Proxy endpoint
	fmt.Println("\n🌐 Proxy Endpoint:")
	cmd = "curl -s -o /dev/null -w '%{http_code}' http://localhost:8000/ 2>/dev/null"
	result = conn.RunCommand(cmd, plugin.WithHideOutput())
	proxyStatus := strings.TrimSpace(result.Stdout)
	if proxyStatus == "404" || proxyStatus == "200" {
		fmt.Printf("✅ Proxy is accessible (HTTP %s - expected for Kong)\n", proxyStatus)
	} else {
		fmt.Printf("❌ Proxy not accessible (HTTP %s)\n", proxyStatus)
	}

	// Check container health
	fmt.Println("\n🐳 Container Health:")
	cmd = "docker ps --filter 'name=kong' --format 'table {{.Names}}\t{{.Status}}' --no-trunc"
	result = conn.RunCommand(cmd, plugin.WithHideOutput())
	if result.Success && result.Stdout != "" {
		fmt.Println(result.Stdout)
	}

	// Check detailed container health
	fmt.Println("\n📋 Detailed Health Status:")
	cmd = "docker inspect kong-gateway --format='{{.State.Health.Status}}' 2>/dev/null"
	result = conn.RunCommand(cmd, plugin.WithHideOutput())
	if result.Success && result.Stdout != "" {
		fmt.Printf("Kong Gateway: %s", strings.TrimSpace(result.Stdout))
	}

	cmd = "docker inspect kong-database --format='{{.State.Health.Status}}' 2>/dev/null"
	result = conn.RunCommand(cmd, plugin.WithHideOutput())
	if result.Success && result.Stdout != "" {
		fmt.Printf("PostgreSQL: %s", strings.TrimSpace(result.Stdout))
	}

	return nil
}

func (p *Plugin) shellHandler(ctx context.Context, conn plugin.Connection, args []string, flags map[string]interface{}) error {
	fmt.Println("🐚 Opening shell in Kong container...")
	return conn.RunInteractive("docker exec -it kong-gateway sh")
}

// Helper functions

func getPassword(flags map[string]interface{}) string {
	if pass, ok := flags["password"].(string); ok {
		return pass
	}
	return ""
}

func getFlag(flags map[string]interface{}, key, defaultValue string) string {
	if value, ok := flags[key].(string); ok {
		return value
	}
	return defaultValue
}

func getString(m map[string]interface{}, key string) string {
	if val, ok := m[key].(string); ok {
		return val
	}
	return ""
}

func getInt(m map[string]interface{}, key string) int {
	switch val := m[key].(type) {
	case float64:
		return int(val)
	case int:
		return val
	case string:
		var result int
		fmt.Sscanf(val, "%d", &result)
		return result
	}
	return 0
}

func getBool(m map[string]interface{}, key string) bool {
	if val, ok := m[key].(bool); ok {
		return val
	}
	return false
}

// generateDockerCompose creates docker-compose.yml with the specified PostgreSQL port
func (p *Plugin) generateDockerCompose(dbPort string) string {
	return fmt.Sprintf(`version: '3.8'

networks:
  kong-net:
    driver: bridge

services:
  kong-db:
    image: postgres:15-alpine
    container_name: kong-database
    restart: unless-stopped
    networks:
      - kong-net
    environment:
      POSTGRES_USER: kong
      POSTGRES_DB: kong
      POSTGRES_PASSWORD: kong_password_change_me
    ports:
      - "%s:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U kong"]
      interval: 10s
      timeout: 5s
      retries: 5
    volumes:
      - kong-db-data:/var/lib/postgresql/data

  kong-migrations:
    image: kong/kong-gateway:3.6.0.0
    container_name: kong-migrations
    restart: on-failure
    networks:
      - kong-net
    command: kong migrations bootstrap
    environment:
      KONG_DATABASE: postgres
      KONG_PG_HOST: kong-db
      KONG_PG_DATABASE: kong
      KONG_PG_USER: kong
      KONG_PG_PASSWORD: kong_password_change_me
    depends_on:
      kong-db:
        condition: service_healthy

  kong:
    image: kong/kong-gateway:3.6.0.0
    container_name: kong-gateway
    restart: unless-stopped
    networks:
      - kong-net
    ports:
      - "8000:8000"
      - "8443:8443"
      - "8001:8001"
      - "8444:8444"
    environment:
      KONG_DATABASE: postgres
      KONG_PG_HOST: kong-db
      KONG_PG_DATABASE: kong
      KONG_PG_USER: kong
      KONG_PG_PASSWORD: kong_password_change_me
      KONG_PROXY_ACCESS_LOG: /dev/stdout
      KONG_ADMIN_ACCESS_LOG: /dev/stdout
      KONG_PROXY_ERROR_LOG: /dev/stderr
      KONG_ADMIN_ERROR_LOG: /dev/stderr
      KONG_ADMIN_LISTEN: "0.0.0.0:8001"
      KONG_ADMIN_GUI_URL: "http://localhost:8002"
    depends_on:
      kong-db:
        condition: service_healthy
      kong-migrations:
        condition: service_completed_successfully
    healthcheck:
      test: ["CMD", "kong", "health"]
      interval: 10s
      timeout: 10s
      retries: 10
    volumes:
      - ./kong.conf:/etc/kong/kong.conf:ro

  konga:
    image: pantsel/konga:latest
    container_name: konga-ui
    restart: unless-stopped
    networks:
      - kong-net
    ports:
      - "8002:1337"
    environment:
      NODE_ENV: production
    depends_on:
      - kong

volumes:
  kong-db-data:
    driver: local
`, dbPort)
}
