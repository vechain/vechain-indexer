#!/bin/bash
DIST="./dist"
mkdir -p "${DIST}"
DOCKER_BUILDKIT=1 docker build --platform=linux/amd64 -o "${DIST}" .
ls -la "${DIST}"