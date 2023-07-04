// Initiate replica set
printjson(rs.initiate())

// Wait for the replica set to finish initialization
while (true) {
    var status = rs.isMaster()
    if (status && status.ismaster) {
        break
    }
    sleep(1000) // Wait for 1 second before checking again
}


/**
 * Create a user if it doesn't exist, otherwise update the password / roles
 * @param db - the database to create the user in
 * @param username - the username
 * @param password - the password
 * @param roles - the roles, see https://docs.mongodb.com/manual/reference/built-in-roles/
 */
function createUser(db, username, password, roles) {
    if (db.system.users.find({user: username}).count() <= 0) {
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
 * @param collection - the collection to configure indexes for
 * @param {Object} config the index configuration
 * @param {Object} config.keys the keys of the index. See https://docs.mongodb.com/manual/reference/method/db.collection.createIndex/#index-creation
 * @param {Object} config.options the options of the index. See https://docs.mongodb.com/manual/reference/method/db.collection.createIndex/#options
 */
function configureIndexes(collection, config) {
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
    [{role: "read", db: "vechain"}]
);

// Create database
console.log("Creating database (vechain)");
db = db.getSiblingDB("vechain");
