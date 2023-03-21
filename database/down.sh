#!/bin/bash

echo "Stopping databases"

docker-compose -f docker-compose-mongo.yaml down