// Initialise Replica Set
rs.initiate()

// Create database
db = db.getSiblingDB("vechain");

// Create collections
db.createCollection("blocks");
db.createCollection("transactions");
db.createCollection("clauses");
db.createCollection("contracts");
db.createCollection("transfer_events");
db.createCollection("nfts");
