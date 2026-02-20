// drop-all-indexes.js
// Drops all non-_id indexes on all collections in the current DB.

const dbName = db.getName();
console.log(`\n== Dropping all non-_id indexes in database: ${dbName} ==\n`);

const collections = db.getCollectionNames();

for (const collName of collections) {
    if (collName.startsWith("system.")) {
        console.log(`Skipping system collection: ${collName}`);
        continue;
    }

    const coll = db.getCollection(collName);

    try {
        const indexes = coll.getIndexes();

        if (indexes.length <= 1) {
            console.log(`[${collName}] No indexes to drop (only _id_)`);
            continue;
        }

        console.log(`\n[${collName}] Indexes BEFORE:`);
        indexes.forEach((idx) => console.log(` - ${idx.name}`));

        const result = coll.dropIndexes(); // drops all except _id_

        console.log(`[${collName}] dropIndexes() result:`);
        console.log(JSON.stringify(result, null, 2));

        const after = coll.getIndexes();
        console.log(`[${collName}] Indexes AFTER:`);
        after.forEach((idx) => console.log(` - ${idx.name}`));

    } catch (err) {
        console.error(`❌ Error processing collection [${collName}]:`, err);
    }
}

console.log(`\n== Done. ==\n`);