const replicaSetName = process.env.MONGO_REPLICA_SET || "rs0"
const replicaSetHost = `${process.env.MONGO_REPLICA_HOST || process.env.MONGO_HOST}:27017`
const maxPrimaryWaitAttempts = Number(process.env.MONGO_PRIMARY_WAIT_ATTEMPTS || 60)

function replicaSetIsNotInitialized(error) {
    return (
        error.codeName === "NotYetInitialized" ||
        error.codeName === "NoReplicationEnabled" ||
        error.message.includes("no replset config has been received")
    )
}

function ensureReplicaSet() {
    try {
        const config = rs.conf()
        const currentHost = config.members[0].host

        if (currentHost !== replicaSetHost) {
            console.log(
                `Updating replica set host from ${currentHost} to ${replicaSetHost}`
            )
            config.members[0].host = replicaSetHost
            config.version = config.version + 1
            printjson(rs.reconfig(config, {force: true}))
        } else {
            console.log(`Replica set already configured for ${replicaSetHost}`)
        }
    } catch (error) {
        if (!replicaSetIsNotInitialized(error)) {
            throw error
        }

        console.log(`Initiating replica set ${replicaSetName} at ${replicaSetHost}`)
        printjson(
            rs.initiate({
                _id: replicaSetName,
                members: [{_id: 0, host: replicaSetHost}],
            })
        )
    }
}

function waitForPrimary() {
    for (let attempt = 1; attempt <= maxPrimaryWaitAttempts; attempt++) {
        const hello = db.adminCommand({hello: 1})
        if (hello && hello.isWritablePrimary) {
            console.log("Replica set primary is ready")
            return
        }

        console.log(
            `Waiting for replica set primary (${attempt}/${maxPrimaryWaitAttempts})`
        )
        sleep(1000)
    }

    throw new Error("Timed out waiting for replica set primary")
}

ensureReplicaSet()
waitForPrimary()

/**
 * Create a user if it doesn't exist, otherwise update the password / roles
 * @param db - the database to create the user in
 * @param username - the username
 * @param password - the password
 * @param roles - the roles, see https://docs.mongodb.com/manual/reference/built-in-roles/
 */
function createUser(db, username, password, roles) {
    const usernameValue = String(username)

    if (db.system.users.findOne({user: String(username)}) == null) {
        console.log(
            "Creating user (" + usernameValue + ") with roles: " + JSON.stringify(roles)
        );
        db.createUser({
            user: usernameValue,
            pwd: password,
            roles: roles,
        });
    } else {
        console.log(
            "User (" + usernameValue + ") already exists, Updating password / roles"
        );
        //modify the user password / roles
        db.updateUser(usernameValue, {
            pwd: password,
            roles: roles,
        });
    }
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
