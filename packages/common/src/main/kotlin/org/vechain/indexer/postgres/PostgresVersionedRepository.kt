package org.vechain.indexer.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.BlockIdentifier

/**
 * Abstract base class for versioned PostgreSQL repositories.
 *
 * Implements the versioned rows pattern where all versions of a record are stored in the same table
 * with an `is_current` flag to identify the active version.
 *
 * Operations:
 * - saveAllVersioned: Marks existing versions as is_current=false, inserts new versions with
 *   is_current=true
 * - rollback: Deletes rows where block_number >= specified block, restores is_current=true for
 *   latest remaining version per entity
 * - prune: Removes old non-current versions beyond a safe block depth
 *
 * Subclasses must implement the template methods to specify table name, columns, and row mapping.
 */
abstract class PostgresVersionedRepository<T : VersionedDocument>(
    protected val jdbcTemplate: JdbcTemplate,
    protected val namedJdbcTemplate: NamedParameterJdbcTemplate,
    protected val objectMapper: ObjectMapper,
) {
    /** Returns the database table name for this entity type. */
    abstract fun tableName(): String

    /** Returns the column names for SELECT queries (excluding entity_id, version, is_current). */
    abstract fun entityIdColumn(): String

    /** Maps a ResultSet row to an entity instance. */
    abstract fun mapRow(rs: ResultSet): T

    /**
     * Returns the parameters for an INSERT statement. Order must match the columns in
     * insertColumns().
     */
    abstract fun insertParams(doc: T): Array<Any?>

    /** Returns the column names for INSERT statements. */
    abstract fun insertColumns(): String

    /** Returns the placeholder values for INSERT statements (e.g., "?, ?, ?::jsonb"). */
    abstract fun insertPlaceholders(): String

    /**
     * Returns the parameters for an INSERT statement for existing/archived records. Same as
     * insertParams() but with is_current=false. Default implementation assumes is_current is at
     * index 2 in the params array (after entity_id and version).
     */
    open fun insertParamsForExisting(doc: T): Array<Any?> {
        val params = insertParams(doc).copyOf()
        params[2] = false // is_current = false
        return params
    }

    /**
     * Saves updated documents with versioning support.
     *
     * This method:
     * 1. INSERTs existing versions (intermediate versions from cache) with is_current=false
     * 2. INSERTs updated versions (final versions) with is_current=true
     *
     * Uses ON CONFLICT to handle duplicate keys when blocks are reprocessed.
     *
     * @param updated List of new document versions to insert (will be marked as current)
     * @param existing List of previous document versions to archive (will be marked as non-current)
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun saveAllVersioned(updated: List<T>, existing: List<T>) {
        if (updated.isEmpty() && existing.isEmpty()) {
            return
        }

        // Insert existing versions (intermediate) with is_current=false
        // Uses ON CONFLICT to handle re-processing of blocks
        if (existing.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO ${tableName()} (${insertColumns()})
                VALUES (${insertPlaceholders()})
                ON CONFLICT (${entityIdColumn()}, version) DO UPDATE SET is_current = false
                """
                    .trimIndent(),
                existing.map { insertParamsForExisting(it) },
            )
        }

        // Insert updated versions (final) with is_current=true
        // Uses ON CONFLICT to handle re-processing of blocks
        if (updated.isNotEmpty()) {
            jdbcTemplate.batchUpdate(
                """
                INSERT INTO ${tableName()} (${insertColumns()})
                VALUES (${insertPlaceholders()})
                ON CONFLICT (${entityIdColumn()}, version) DO UPDATE SET is_current = true
                """
                    .trimIndent(),
                updated.map { insertParams(it) },
            )
        }
    }

    /**
     * Rolls back all changes at or after the specified block number.
     *
     * This method:
     * 1. Deletes all rows where block_number >= blockNumber
     * 2. Restores is_current=true for the max version of each affected entity
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun rollback(blockNumber: Long) {
        // First, find affected entity IDs before deleting
        val affectedEntityIds =
            jdbcTemplate.queryForList(
                """
                SELECT DISTINCT ${entityIdColumn()} 
                FROM ${tableName()} 
                WHERE block_number >= ?
                """
                    .trimIndent(),
                String::class.java,
                blockNumber,
            )

        if (affectedEntityIds.isEmpty()) {
            return
        }

        // Delete rows at or after the rollback block
        jdbcTemplate.update(
            """
            DELETE FROM ${tableName()} 
            WHERE block_number >= ?
            """
                .trimIndent(),
            blockNumber,
        )

        // Restore is_current=true for the max version of each affected entity
        namedJdbcTemplate.update(
            """
            UPDATE ${tableName()} t
            SET is_current = true
            WHERE (${entityIdColumn()}, version) IN (
                SELECT ${entityIdColumn()}, MAX(version)
                FROM ${tableName()}
                WHERE ${entityIdColumn()} IN (:entityIds)
                GROUP BY ${entityIdColumn()}
            )
            """
                .trimIndent(),
            mapOf("entityIds" to affectedEntityIds),
        )
    }

    /**
     * Gets the latest block identifier from the table.
     *
     * @return The BlockIdentifier of the most recent block, or null if the table is empty
     */
    open fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id
                FROM ${tableName()}
                WHERE is_current = true
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                BlockIdentifier(number = rs.getLong("block_number"), id = rs.getString("block_id"))
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    /**
     * Finds the current version of an entity by its ID.
     *
     * @param entityId The entity's unique identifier
     * @return The current version of the entity, or null if not found
     */
    open fun findCurrentByEntityId(entityId: String): T? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE ${entityIdColumn()} = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                entityId,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    /** Helper to get a Long value that might be null from ResultSet. */
    protected fun ResultSet.getLongOrNull(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    /** Helper to get an Int value that might be null from ResultSet. */
    protected fun ResultSet.getIntOrNull(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }
}
