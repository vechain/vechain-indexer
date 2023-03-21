#!/bin/bash

echo "
-> 1. Setting up replica set"

docker-compose -f docker-compose-mongo-setup.yaml up

echo "
-> 2. Removing mongosetup container"
docker rm mongo-setup --force