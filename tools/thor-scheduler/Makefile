SHELL := /bin/bash

GO ?= go
GOTOOLCHAIN ?= auto
BINARY := thor-scheduler

.PHONY: build
build:
	GOTOOLCHAIN=$(GOTOOLCHAIN) $(GO) build -o $(BINARY) .

.PHONY: clean
clean:
	rm -f $(BINARY)

.PHONY: tidy
tidy:
	GOTOOLCHAIN=$(GOTOOLCHAIN) $(GO) mod tidy
