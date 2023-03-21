#!/bin/bash

echo "Cleaning databases out"

docker-compose -f docker-compose-mongo.yaml down -v --remove-orphans