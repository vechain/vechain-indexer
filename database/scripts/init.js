db = db.getSiblingDB("vechain");

db.createCollection("blocks");
db.createCollection("transactions");
db.createCollection("clauses");
db.createCollection("contracts");
