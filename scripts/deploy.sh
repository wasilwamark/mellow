#!/bin/sh
# This script mimics the GitHub CLI (gh) release creation
# It uses goreleaser to build and publish a release locally

if [ -z "$GITHUB_TOKEN" ]; then
    echo "Error: GITHUB_TOKEN environment variable is not set."
    echo "Please set it to allow goreleaser to publish to GitHub."
    exit 1
fi

# Ensure we are on a tag
if ! git describe --exact-match --tags HEAD >/dev/null 2>&1; then
    echo "Error: You must be on a git tag to run a release."
    echo "Create a tag first: git tag v1.0.0"
    exit 1
fi

echo "Running GoReleaser to build and publish..."
goreleaser release --clean
