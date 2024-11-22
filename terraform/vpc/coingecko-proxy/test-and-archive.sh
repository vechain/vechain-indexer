#!/bin/bash
set -exo pipefail
pwd
DIR=`dirname $0`
DIST="${DIR}/../../dist/coingecko-proxy"
mkdir -pv "${DIST}"
docker info
DOCKER_BUILDKIT=1 docker build --platform=linux/amd64 -o "${DIST}" .
ls -la "${DIST}"