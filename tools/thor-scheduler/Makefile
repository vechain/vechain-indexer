SHELL := /bin/bash

# Locate a usable Go binary. Order: explicit $GO, PATH, common SDK install locations.
# Most contributors won't have go on PATH but will have it under ~/sdk/<version>/bin/go
# (the layout `go install golang.org/dl/goX.Y@latest && goX.Y download` produces).
GO ?= $(shell \
	if command -v go >/dev/null 2>&1; then \
		command -v go; \
	elif compgen -G "$$HOME/sdk/go*/bin/go" >/dev/null; then \
		ls -1d $$HOME/sdk/go*/bin/go | sort -V | tail -1; \
	elif [ -x /opt/homebrew/bin/go ]; then \
		echo /opt/homebrew/bin/go; \
	elif [ -x /usr/local/go/bin/go ]; then \
		echo /usr/local/go/bin/go; \
	fi)

# Force GOTOOLCHAIN=auto on the build command so the toolchain in go.mod is downloaded
# transparently when the local Go is older. This overrides any GOTOOLCHAIN=local that may be set
# in the user's shell.
BINARY := thor-scheduler

.PHONY: build
build:
	@if [ -z "$(GO)" ]; then \
		echo "ERROR: no Go binary found. Install Go (>= 1.26) or set GO=/path/to/go." >&2; \
		exit 1; \
	fi
	@echo "Using Go: $(GO)"
	GOTOOLCHAIN=auto $(GO) build -trimpath -o $(BINARY) .

.PHONY: clean
clean:
	rm -f $(BINARY)

.PHONY: tidy
tidy:
	@if [ -z "$(GO)" ]; then echo "ERROR: no Go binary found." >&2; exit 1; fi
	GOTOOLCHAIN=auto $(GO) mod tidy
