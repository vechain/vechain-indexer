package org.vechain.indexer.stargate.vetDelegated

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `vet_delegated.total_by_block`: one row per changed block, rollback by block. */
@Repository
@ConditionalOnPostgres
open class VetDelegatedWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(records: List<VetDelegatedByBlock>) {
        if (records.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = records.associateBy { it.blockNumber }.values.toList()
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, d -> VetDelegatedRowMapping.bind(ps, d) }
    }

    /** The newest row, from which the service resumes its rollover totals. */
    open fun latest(): VetDelegatedByBlock? =
        jdbc
            .query(
                "SELECT * FROM vet_delegated.total_by_block ORDER BY block_number DESC LIMIT 1"
            ) { rs, _ ->
                VetDelegatedRowMapping.read(rs)
            }
            .firstOrNull()

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM vet_delegated.total_by_block WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE vet_delegated.total_by_block")
    }

    companion object {
        private val INSERT =
            "INSERT INTO vet_delegated.total_by_block (block_number, " +
                VetDelegatedRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, " +
                VetDelegatedRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (block_number) DO UPDATE SET " +
                VetDelegatedRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
