.PHONY: install clean test

# Install to system (build and install globally)
install:
	@echo "Building mellow..."
	go build -o /tmp/mellow ./cmd/mellow
	@echo "Installing to /usr/local/bin..."
	sudo cp /tmp/mellow /usr/local/bin/mellow && sudo chmod +x /usr/local/bin/mellow
	@rm -f /tmp/mellow
	@echo "✅ Installation complete! Run: mellow --help"

# Build for multiple platforms
build-all:
	@echo "Building for multiple platforms..."
	GOOS=linux GOARCH=amd64 go build -o mellow-linux-amd64 ./cmd/mellow
	GOOS=linux GOARCH=arm64 go build -o mellow-linux-arm64 ./cmd/mellow
	GOOS=darwin GOARCH=amd64 go build -o mellow-darwin-amd64 ./cmd/mellow
	GOOS=darwin GOARCH=arm64 go build -o mellow-darwin-arm64 ./cmd/mellow
	GOOS=windows GOARCH=amd64 go build -o mellow-windows-amd64.exe ./cmd/mellow
	@echo "All builds completed"

# Clean build artifacts
clean:
	@echo "Cleaning..."
	@rm -rf bin/ /tmp/mellow 2>/dev/null || true

# Run tests
test:
	go test ./...

# Download dependencies
deps:
	go mod download
	go mod tidy

# Format code
fmt:
	go fmt ./...

# Lint code
lint:
	golangci-lint run

