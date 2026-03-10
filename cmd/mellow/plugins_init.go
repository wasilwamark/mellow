package main

import (
	"github.com/wasilwamark/mellow/internal/core/alias"
	pluginmanager "github.com/wasilwamark/mellow/internal/core/plugin-manager"

	"github.com/wasilwamark/mellow/internal/services/docker"
	"github.com/wasilwamark/mellow/internal/services/fail2ban"
	"github.com/wasilwamark/mellow/internal/services/firewall"
	"github.com/wasilwamark/mellow/internal/services/keycloak"
	"github.com/wasilwamark/mellow/internal/services/kong"
	"github.com/wasilwamark/mellow/internal/services/mysql"
	"github.com/wasilwamark/mellow/internal/services/nginx"
	"github.com/wasilwamark/mellow/internal/services/redis"
	"github.com/wasilwamark/mellow/internal/services/restic"
	"github.com/wasilwamark/mellow/internal/services/runtimes"
	"github.com/wasilwamark/mellow/internal/services/system"
	"github.com/wasilwamark/mellow/internal/services/wireguard"
	"github.com/wasilwamark/mellow/internal/services/wordpress"
	"github.com/wasilwamark/mellow/pkg/plugin"
)

// initializeBuiltinPlugins registers all built-in plugins
func initializeBuiltinPlugins() {
	// Register core plugins
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/core/alias", alias.NewPlugin())
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/core/plugin-manager", pluginmanager.NewPlugin())

	// Register service plugins
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/system", &system.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/docker", &docker.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/fail2ban", &fail2ban.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/firewall", &firewall.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/keycloak", &keycloak.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/mysql", &mysql.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/nginx", &nginx.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/wireguard", &wireguard.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/redis", &redis.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/restic", &restic.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/runtimes", &runtimes.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/wordpress", &wordpress.Plugin{})
	plugin.RegisterBuiltin("github.com/wasilwamark/mellow/services/kong", &kong.Plugin{})
}
