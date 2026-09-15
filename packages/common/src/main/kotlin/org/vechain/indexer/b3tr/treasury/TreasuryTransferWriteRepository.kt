package org.vechain.indexer.b3tr.treasury

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.treasury.TreasuryTransferRowMapping.COLUMNS
import org.vechain.indexer.b3tr.treasury.TreasuryTransferRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `b3tr_treasury.transfer`, append-only: a rollback deletes the blocks it undoes. */
@Repository
@ConditionalOnPostgres
open class TreasuryTransferWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(transfers: List<TreasuryTransfer>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = transfers.associateBy { it.id }.values.toList()
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, t ->
            TreasuryTransferRowMapping.bind(ps, t)
        }
    }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM $TABLE WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE $TABLE")
    }

    companion object {
        private val INSERT =
            "INSERT INTO $TABLE (id, " +
                COLUMNS.joinToString() +
                ") VALUES (?, " +
                COLUMNS.joinToString {
                    if (it == "category") "CAST(? AS b3tr_treasury.category)" else "?"
                } +
                ") ON CONFLICT (id) DO UPDATE SET " +
                COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
