#!/bin/bash

echo "Starting databases"

docker-compose -f docker-compose-mongo.yaml up -d