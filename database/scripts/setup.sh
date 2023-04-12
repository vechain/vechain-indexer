#!/bin/bash
sleep 5

mongosh --host $1:27017 --username $2 --password $3 /scripts/init.js
mongosh --host $1:27017 --username $2 --password $3 /scripts/add-builtin-contracts.js
