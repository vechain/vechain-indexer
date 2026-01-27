package org.vechain.indexer.pruner

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional

/**
 * Reusable pruner for versioned PostgreSQL tables.
 *
 * Removes old non-current versions that are beyond a safe block depth, keeping only the most recent
 * archived version per entity for potential rollback scenarios.
 *
 * The pruning strategy:
 * - Only deletes rows where is_current = false (archived versions)
 * - Only deletes rows where block_number < (currentBlockNumber - pruneBlockDepth)
 * - Keeps at least one archived version per entity (the most recent one before the prune threshold)
 */
open class PostgresPruner(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
    private val pruneBlockDepth: Long,
    private val tableName: String,
    private val entityIdColumn: String = "entity_id",
) : PostgresTargetedPruner {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun run(currentBlockNumber: Long) {
        run(currentBlockNumber, null)
    }

    override fun run(currentBlockNumber: Long, entityIds: List<String>?) {
        prune(currentBlockNumber, entityIds)
    }

    /**
     * Prunes old non-current versions from the configured table.
     *
     * @param currentBlockNumber The current block number being processed
     * @param entityIds Optional list of specific entity IDs to prune (for targeted pruning)
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun prune(currentBlockNumber: Long, entityIds: List<String>? = null) {
        val pruneThreshold = currentBlockNumber - pruneBlockDepth

        if (pruneThreshold <= 0) {
            logger.debug(
                "Skipping prune for {} - current block {} minus depth {} is not positive",
                tableName,
                currentBlockNumber,
                pruneBlockDepth,
            )
            return
        }

        val deletedCount =
            if (entityIds.isNullOrEmpty()) {
                // Prune all entities - delete non-current versions older than threshold,
                // but keep the most recent archived version per entity
                jdbcTemplate.update(
                    """
                DELETE FROM $tableName
                WHERE is_current = false
                AND block_number < ?
                AND ($entityIdColumn, version) NOT IN (
                    SELECT $entityIdColumn, MAX(version)
                    FROM $tableName
                    WHERE is_current = false AND block_number < ?
                    GROUP BY $entityIdColumn
                )
                """
                        .trimIndent(),
                    pruneThreshold,
                    pruneThreshold,
                )
            } else {
                // Targeted prune - only prune specified entities
                namedJdbcTemplate.update(
                    """
                DELETE FROM $tableName
                WHERE is_current = false
                AND block_number < :threshold
                AND $entityIdColumn IN (:entityIds)
                AND ($entityIdColumn, version) NOT IN (
                    SELECT $entityIdColumn, MAX(version)
                    FROM $tableName
                    WHERE is_current = false 
                    AND block_number < :threshold 
                    AND $entityIdColumn IN (:entityIds)
                    GROUP BY $entityIdColumn
                )
                """
                        .trimIndent(),
                    mapOf("threshold" to pruneThreshold, "entityIds" to entityIds),
                )
            }

        if (deletedCount > 0) {
            logger.debug(
                "Pruned {} old versions from {} (threshold block: {})",
                deletedCount,
                tableName,
                pruneThreshold,
            )
        }
    }
}
