# Mellow — build automation (Java 25)
#
#   make              build the runnable jar (default)
#   make test         run the unit tests
#   make run          build and launch
#   make install      install a launcher into $(PREFIX)/bin (default /usr/local)
#   make help         list every target
#
# Maven must run on JDK 25; JAVA_HOME should point at a JDK 25 install.

# ---- configuration ---------------------------------------------------------
MVN       ?= mvn
MVN_FLAGS ?=
PREFIX    ?= /usr/local
BINDIR    ?= $(PREFIX)/bin
DESTDIR   ?=
APP_NAME  ?= mellow
JAR       ?= target/mellow.jar
JAVA_BIN  ?= $(if $(JAVA_HOME),$(JAVA_HOME)/bin/java,java)
ARGS      ?=
TEST      ?=

.DEFAULT_GOAL := build

.PHONY: help build package jar test test-one verify coverage run install uninstall native deps hooks version clean distclean

# ---- help ------------------------------------------------------------------
help: ## Show this help
	@echo "Mellow build targets:"
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

# ---- build -----------------------------------------------------------------
build: package ## Build the runnable jar (default)

package: ## Build the runnable fat jar ($(JAR))
	$(MVN) $(MVN_FLAGS) -q -DskipTests package

jar: package ## Alias for package

test: ## Run the unit test suite
	$(MVN) $(MVN_FLAGS) test

test-one: ## Run a single test class: make test-one TEST=MellowConfigTest
	@test -n "$(TEST)" || { echo "usage: make test-one TEST=<ClassName>"; exit 1; }
	$(MVN) $(MVN_FLAGS) -Dtest='$(TEST)' test

verify: ## Run the full Maven verify lifecycle
	$(MVN) $(MVN_FLAGS) verify

coverage: ## Run tests and generate the JaCoCo report (target/site/jacoco)
	$(MVN) $(MVN_FLAGS) -q verify
	@echo "📊 Coverage report: target/site/jacoco/index.html"

run: package ## Build and run (pass arguments with ARGS="...")
	$(JAVA_BIN) -jar $(JAR) $(ARGS)

# ---- install ---------------------------------------------------------------
install: package ## Install launcher + jar into $(BINDIR) (override PREFIX=...)
	@echo "Installing $(APP_NAME) into $(DESTDIR)$(BINDIR)..."
	install -d "$(DESTDIR)$(BINDIR)"
	install -m 0644 "$(JAR)" "$(DESTDIR)$(BINDIR)/$(APP_NAME).jar"
	@printf '#!/bin/sh\nexec "%s" -jar "%s/%s.jar" "$$@"\n' \
		"$(JAVA_BIN)" "$(BINDIR)" "$(APP_NAME)" > "$(DESTDIR)$(BINDIR)/$(APP_NAME)"
	@chmod 0755 "$(DESTDIR)$(BINDIR)/$(APP_NAME)"
	@echo "✅ Installed. Run: $(APP_NAME) --help"

uninstall: ## Remove the installed launcher and jar
	rm -f "$(DESTDIR)$(BINDIR)/$(APP_NAME)" "$(DESTDIR)$(BINDIR)/$(APP_NAME).jar"
	@echo "✅ Uninstalled from $(DESTDIR)$(BINDIR)"

# ---- native ----------------------------------------------------------------
native: ## Build a GraalVM native image (requires GraalVM for JDK 25)
	@command -v native-image >/dev/null 2>&1 || { \
		echo "❌ native-image not found. Install GraalVM for JDK 25 and put it on PATH."; exit 1; }
	@native-image --version 2>/dev/null | grep -qE '25\.' || { \
		echo "❌ native-image must be GraalVM for JDK 25 (found: $$(native-image --version 2>/dev/null | head -1))."; \
		echo "   The jar targets class file version 69 (Java 25)."; exit 1; }
	$(MVN) $(MVN_FLAGS) -Pnative package

# ---- maintenance -----------------------------------------------------------
deps: ## Resolve and download dependencies
	$(MVN) $(MVN_FLAGS) -q dependency:resolve

hooks: ## Enable the repository's versioned git hooks
	git config core.hooksPath .githooks
	@echo "✅ Git hooks enabled (.githooks)."

version: ## Print the project version
	@$(MVN) $(MVN_FLAGS) -q help:evaluate -Dexpression=project.version -DforceStdout

clean: ## Remove build output
	$(MVN) $(MVN_FLAGS) -q clean

distclean: clean ## Remove build output and stray class files
	find . -name '*.class' -delete
	@echo "✅ Cleaned."
