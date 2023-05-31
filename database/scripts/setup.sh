#!/bin/bash
sleep 5

mongosh --host $1:27017 /scripts/init.js
