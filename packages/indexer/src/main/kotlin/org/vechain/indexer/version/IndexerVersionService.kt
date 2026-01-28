package org.vechain.indexer.version

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.model.BlockIdentifier

@Service
open class IndexerVersionService(
    private val repo: IndexerVersionRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(IndexerVersionService::class.java)

    /**
     * Retrieves the current version of the indexer by table name.
     *
     * @param tableName The name of the table to fetch the version for.
     * @return The stored version number for the indexer, or `null` if no versioned document is
     *   found.
     */
    fun getStoredIndexerVersion(tableName: String): Int? = repo.findByTableName(tableName)?.version

    /**
     * Gets the last processed block for an indexer.
     *
     * @param indexerName The name of the indexer.
     * @return The last processed block identifier, or null if not found.
     */
    fun getLastProcessedBlock(indexerName: String): BlockIdentifier? {
        val indexer = repo.findById(indexerName)
        return indexer?.lastProcessedBlock
    }

    /**
     * Updates the version of the indexer.
     *
     * @param indexerName The name of the indexer to update.
     * @param tableName The database table name for this indexer.
     * @param newVersion The new version number to set for the indexer.
     */
    fun updateIndexerVersion(indexerName: String, tableName: String, newVersion: Int) {
        val updated =
            repo.findById(indexerName)?.copy(version = newVersion, lastProcessedBlock = null)
                ?: IndexerVersion(
                    indexerName = indexerName,
                    tableName = tableName,
                    version = newVersion,
                    lastProcessedBlock = null,
                )
        repo.save(updated)
        logger.info("Updated indexer version for $indexerName to $newVersion")
    }

    /**
     * Updates the last safely synced block for an indexer.
     *
     * @param indexerName The name of the indexer.
     * @param block The block identifier to store.
     */
    fun updateLastSafeSyncedBlock(indexerName: String, block: BlockIdentifier?) {
        if (block == null) return
        val indexer = repo.findById(indexerName)
        if (indexer != null) {
            val updatedIndexer = indexer.copy(lastProcessedBlock = block)
            repo.save(updatedIndexer)
        }
    }

    /**
     * Ensures the table exists and is at the correct version. This is the main entry point for
     * table management. It handles:
     * - Creating the table if no version record exists
     * - Dropping and recreating the table if the version has increased
     * - Doing nothing if the table is already at the correct version
     *
     * @param indexerName The name of the indexer.
     * @param tableName The database table name for this indexer.
     * @param schemaResource The classpath resource path to the schema SQL file (e.g.,
     *   "db/tables/contracts.sql").
     * @param newVersion The new version number of the indexer.
     * @return True if the table was created/recreated, false if it already existed at correct
     *   version.
     */
    fun ensureTableExists(
        indexerName: String,
        tableName: String,
        schemaResource: String,
        newVersion: Int,
    ): Boolean {
        try {
            val storedVersion = getStoredIndexerVersion(tableName)

            if (storedVersion == null) {
                // Table doesn't exist yet - create it
                logger.info("No version record found for $tableName. Creating table from schema.")
                createTable(schemaResource)
                updateIndexerVersion(indexerName, tableName, newVersion)
                return true
            }

            if (storedVersion < newVersion) {
                // Version changed - drop and recreate
                logger.info("Version changed for $tableName: $storedVersion -> $newVersion")
                dropAndRecreateTable(tableName, schemaResource)
                updateIndexerVersion(indexerName, tableName, newVersion)
                return true
            }

            // Already at correct version
            logger.debug("Table $tableName is already at version $storedVersion")
            return false
        } catch (e: Exception) {
            logger.error("Error ensuring table exists for $indexerName", e)
            throw e
        }
    }

    /**
     * Creates a table by executing the entire schema file. Each schema file should contain exactly
     * one table definition with all its indexes.
     *
     * @param schemaResource The classpath resource path to the schema SQL file.
     */
    fun createTable(schemaResource: String) {
        logger.info("Creating table from schema: $schemaResource")
        val schemaContent = readSchemaResource(schemaResource)
        executeSchemaStatements(schemaContent)
        logger.info("Successfully created table from schema: $schemaResource")
    }

    /**
     * Drops the specified PostgreSQL table and recreates it from the schema file.
     *
     * @param tableName The name of the table to drop and recreate.
     * @param schemaResource The classpath resource path to the schema SQL file.
     */
    fun dropAndRecreateTable(tableName: String, schemaResource: String) {
        logger.info("Dropping and recreating table $tableName from schema $schemaResource")

        // Drop the table
        jdbcTemplate.execute("DROP TABLE IF EXISTS \"$tableName\" CASCADE")
        logger.info("Successfully dropped table: $tableName")

        // Recreate from schema
        val schemaContent = readSchemaResource(schemaResource)
        executeSchemaStatements(schemaContent)
        logger.info("Successfully recreated table: $tableName")
    }

    /**
     * Executes all SQL statements from schema content. Statements are separated by semicolons.
     * Comments and empty lines are ignored.
     *
     * @param schemaContent The full content of the schema SQL file.
     */
    private fun executeSchemaStatements(schemaContent: String) {
        // Split by semicolon, filter out comments and empty statements
        schemaContent
            .split(";")
            .map { it.trim() }
            .filter { statement ->
                statement.isNotBlank() &&
                    !statement.startsWith("--") &&
                    statement.lines().any { line ->
                        val trimmed = line.trim()
                        trimmed.isNotBlank() && !trimmed.startsWith("--")
                    }
            }
            .forEach { statement ->
                logger.debug("Executing: ${statement.take(80)}...")
                jdbcTemplate.execute(statement)
            }
    }

    /**
     * Reads a schema SQL file from the classpath.
     *
     * @param schemaResource The classpath resource path (e.g., "db/tables/contracts.sql").
     * @return The contents of the schema file as a string.
     */
    open fun readSchemaResource(schemaResource: String): String {
        val resource = ClassPathResource(schemaResource)
        return resource.inputStream.bufferedReader().use { it.readText() }
    }
}
