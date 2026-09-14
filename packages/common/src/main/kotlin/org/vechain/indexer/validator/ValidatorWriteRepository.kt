package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `validator.state` as a temporal table; see [NftBlacklistWriteRepository] for the pattern. */
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
    }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM validator.state WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE validator.state SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE validator.state")
    }

    /** The store records [before] once rows are gone and refuses any rollback below it. */
    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM validator.state WHERE superseded_at < ?", before)

    companion object {
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
