package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `validator.state` as a temporal table, plus the slot ledger and the never-pruned cycle table. */
@Repository
@ConditionalOnPostgres
open class ValidatorWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the states block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(validators: List<Validator>) {
        validators
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, rows) -> saveBlock(blockNumber, rows) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows.
    private fun saveBlock(blockNumber: Long, validators: List<Validator>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = validators.associateBy { it.id.lowercase() }.values.toList()
        val ids = rows.map { PostgresHex.bytes(it.id) }
        jdbc.update(
            "UPDATE validator.state SET superseded_at = ? " +
                "WHERE id = ANY(?) AND superseded_at IS NULL AND block_number < ?",
            blockNumber,
            ids.toTypedArray(),
            blockNumber,
        )
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, v -> ValidatorRowMapping.bind(ps, v) }
        jdbc.update(CLOSE_CYCLES, blockNumber, ids.toTypedArray())
        jdbc.update(OPEN_CYCLES, blockNumber, ids.toTypedArray())
    }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM validator.slot WHERE block_number >= ?", blockNumber)
        jdbc.update("DELETE FROM validator.cycle WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE validator.cycle SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
        jdbc.update("DELETE FROM validator.state WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE validator.state SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE validator.state, validator.slot, validator.cycle")
    }

    /** The store records [before] once rows are gone and refuses any rollback below it. */
    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM validator.state WHERE superseded_at < ?", before)

    companion object {
        private const val CYCLE_FIELDS =
            "status, start_block, cycle_period_length, exit_block, completed_periods, " +
                "delegator_vet_staked"

        // A block's states close the cycle rows whose fields they change; never pruned.
        private val CLOSE_CYCLES =
            "UPDATE validator.cycle c SET superseded_at = s.block_number FROM validator.state s " +
                "WHERE s.block_number = ? AND s.id = ANY(?) AND c.id = s.id " +
                "AND c.superseded_at IS NULL AND c.block_number < s.block_number " +
                "AND (${prefixed("c")}) IS DISTINCT FROM (${prefixed("s")})"

        // ...and open one where none stands below the block; a replayed block rewrites its own.
        private val OPEN_CYCLES =
            "INSERT INTO validator.cycle (id, block_number, block_id, block_timestamp, " +
                "$CYCLE_FIELDS) SELECT s.id, s.block_number, s.block_id, s.block_timestamp, " +
                "${prefixed("s")} FROM validator.state s WHERE s.block_number = ? " +
                "AND s.id = ANY(?) AND NOT EXISTS (SELECT 1 FROM validator.cycle c " +
                "WHERE c.id = s.id AND c.superseded_at IS NULL AND c.block_number < s.block_number) " +
                "ON CONFLICT (id, block_number) DO UPDATE SET " +
                ("block_id, block_timestamp, $CYCLE_FIELDS").split(", ").joinToString {
                    "$it = EXCLUDED.$it"
                }

        private fun prefixed(alias: String) = CYCLE_FIELDS.split(", ").joinToString { "$alias.$it" }

        private val INSERT =
            "INSERT INTO validator.state (id, block_number, " +
                ValidatorRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                ValidatorRowMapping.COLUMNS.joinToString {
                    if (it == "status") "CAST(? AS validator.status)" else "?"
                } +
                ") ON CONFLICT (id, block_number) DO UPDATE SET " +
                ValidatorRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
