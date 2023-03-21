#!/bin/bash
sleep 5

if [[ "$1" == "" || "$2" == "" || "$3" == "" ]]; then
  echo "ERROR: Please provide three input params. The PRIMARY and two SECONDARY mongo instances in the replication set. 
    The replication set could not be initialised.
    N.B. We currently only support a 3 node replication set."
  exit 1
fi

echo SETUP.sh time now: `date +"%T" `
mongosh --host $1:27017 <<EOF
  var cfg = {
    "_id": "rs0",
    "version": 1,
    "protocolVersion": NumberLong(1),
    "members": [
      {
        "_id": 0,
        "host": "$1:27017",
        "priority": 2
      },
      {
        "_id": 1,
        "host": "$2:27018",
        "priority": 1
      },
      {
        "_id": 2,
        "host": "$3:27019",
        "priority": 0
      }
    ],
    "settings" : {
      "chainingAllowed" : true,
      "heartbeatIntervalMillis" : 2000,
      "heartbeatTimeoutSecs" : 10,
      "electionTimeoutMillis" : 10000,
      "catchUpTimeoutMillis" : -1,
      "getLastErrorModes" : {

      },
      "getLastErrorDefaults" : {
         "w" : 1,
         "wtimeout" : 0
      }
   }
  };
  rs.initiate(cfg, { force: true });
  rs.reconfig(cfg, { force: true });
  db.getMongo().setReadPref('nearest');

  while (! db.isMaster().ismaster ) { sleep(1000) }
EOF

mongosh --host $1:27017 /scripts/init.js
