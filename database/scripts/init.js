// Initialise Replica Set
//rs.initiate()

/**
 * Create a user if it doesn't exist, otherwise update the password / roles
 * @param db - the database to create the user in
 * @param username - the username
 * @param password - the password
 * @param roles - the roles, see https://docs.mongodb.com/manual/reference/built-in-roles/
 */
function createUser(db, username, password, roles) {
  if (db.system.users.find({ user: username }).count() <= 0) {
    console.log(
      "Creating user (" + username + ") with roles: " + JSON.stringify(roles)
    );
    db.createUser({
      user: username,
      pwd: password,
      roles: roles,
    });
  } else {
    console.log(
      "User (" + username + ") already exists, Updating password / roles"
    );
    //modify the user password / roles
    db.updateUser(username, {
      pwd: password,
      roles: roles,
    });
  }
}

/**
 * Create a collection if it doesn't exist
 * @param db - the database to create the collection in
 * @param collectionName - the name of the collection
 */
function createCollection(db, collectionName) {
  if (db.getCollectionNames().indexOf(collectionName) === -1) {
    console.log("Creating collection: " + collectionName);
    db.createCollection(collectionName);
  } else {
    console.log("Collection (" + collectionName + ") already exists");
  }
}

/**
 * Configure indexes for a collection
 * - create all indexes in the config
 * - remove any indexes that are NOT in the config
 * @param collection - the collection to configure indexes for
 * @param {Object} config the index configuration
 * @param {Object} config.name the name of the index. See https://docs.mongodb.com/manual/reference/method/db.collection.createIndex/#options
 * @param {Object} config.keys the keys of the index. See https://docs.mongodb.com/manual/reference/method/db.collection.createIndex/#index-creation
 * @param {Object} config.options the options of the index. See https://docs.mongodb.com/manual/reference/method/db.collection.createIndex/#options
 */
function configureIndexes(collection, config) {
  //Remove any current indexes that are NOT in the config
  collection.getIndexes().forEach(function (index) {
    if (
      index.name !== "_id_" &&
      !config.find(function (c) {
        return c.options.name === index.name;
      })
    ) {
      console.log("Dropping index " + index.name);
      collection.dropIndex(index.name);
    }
  });

  //Create any indexes that are in the config
  config.forEach(function (index) {
    console.log(
      "Creating index (" +
        collection.getName() +
        "): " +
        JSON.stringify(index.keys)
    );
    collection.createIndex(index.keys, index.options);
  });
}

adminDb = db.getSiblingDB("admin");

//Create "indexer" user if it doesn't exist
createUser(
  adminDb,
  process.env.MONGO_INDEXER_USER,
  process.env.MONGO_INDEXER_PASSWORD,
  [
    {
      role: "readWrite",
      db: "vechain",
    },
  ]
);

//Create "api" user if it doesn't exist
createUser(
  adminDb,
  process.env.MONGO_API_USER,
  process.env.MONGO_API_PASSWORD,
  [{ role: "read", db: "vechain" }]
);

// Create database
db = db.getSiblingDB("vechain");

// Create collections
createCollection(db, "blocks");
createCollection(db, "transactions");
createCollection(db, "clauses");
createCollection(db, "contracts");
createCollection(db, "transfer_events");
createCollection(db, "nfts");

// Create indexes

// blocks
configureIndexes(db.blocks, [
  {
    keys: { blockNumber: -1 },
    options: {
      name: "block_blockNumber_-1",
      unique: true,
    },
  },
  {
    keys: { isFinalized: 1 },
    options: {
      name: "block_isFinalized_1",
    },
  },
]);

// transactions
configureIndexes(db.transactions, [
  {
    keys: { blockNumber: -1 },
    options: {
      name: "tx_blockNumber_-1",
    },
  },
  {
    keys: { origin: 1, blockNumber: -1, _id: -1 },
    options: {
      name: "tx_origin_1_blockNumber_-1__id_-1",
    },
  },
  {
    keys: { gasPayer: 1, blockNumber: -1, _id: -1 },
    options: {
      name: "tx_gasPayer_1_blockNumber_-1__id_-1",
    },
  },
]);

// clauses
configureIndexes(db.clauses, [
  {
    keys: { blockNumber: -1 },
    options: {
      name: "clause_blockNumber_-1",
    },
  },
  {
    keys: { origin: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "clause_origin_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
  {
    keys: { to: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "clause_to_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
]);

// contracts
configureIndexes(db.contracts, [
  {
    keys: { blockNumber: -1 },
    options: {
      name: "contract_blockNumber_-1",
    },
  },
  {
    keys: { creator: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "contract_creator_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
]);

// transfer_events
configureIndexes(db.transfer_events, [
  {
    keys: { blockNumber: -1 },
    options: {
      name: "transfer_blockNumber_-1",
    },
  },
  {
    keys: { to: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "transfer_to_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
  {
    keys: { from: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "transfer_from_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
  {
    keys: { tokenAddress: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
]);

// nfts
configureIndexes(db.nfts, [
  {
    keys: { blockNumber: -1 },
    options: {
      name: "nft_blockNumber_-1",
    },
  },
  {
    keys: { contractAddress: 1, tokenId: 1 },
    options: {
      name: "nft_contractAddress_1_tokenId_1",
      unique: true,
    },
  },
  {
    keys: { owner: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "nft_owner_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
  {
    keys: { contractAddress: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
  {
    keys: { owner: 1, contractAddress: 1, blockNumber: -1, txId: -1, _id: -1 },
    options: {
      name: "nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
  {
    keys: {
      owner: 1,
      contractAddress: 1,
      tokenId: 1,
      blockNumber: -1,
      txId: -1,
      _id: -1,
    },
    options: {
      name: "nft_owner_1_contractAddress_1_tokenId_1_blockNumber_-1_txId_-1__id_-1",
    },
  },
]);
