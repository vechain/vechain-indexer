#!/bin/bash
sleep 5

mongosh --host $1:27017 /scripts/init.js
mongosh --host $1:27017 /scripts/add-builtin-contracts.js
