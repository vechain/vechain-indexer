package org.vechain.indexer.transfer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.transfer.TransferRowMapping.COLUMNS
import org.vechain.indexer.transfer.TransferRowMapping.INTERACTION_COLUMNS
import org.vechain.indexer.transfer.TransferRowMapping.INTERACTION_TABLE
import org.vechain.indexer.transfer.TransferRowMapping.TABLE

/** The `transfers` schema, append-only: a replayed block is a no-op on both tables. */
@Repository
@ConditionalOnPostgres
open class TransferWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(
        transfers: List<IndexedTransferEvent>,
        interactions: List<FungibleTokenInteraction>,
    ) {
        if (transfers.isNotEmpty()) {
            val rows = transfers.distinctBy { it.id }
            jdbc.batchUpdate(INSERT, rows, rows.size) { ps, t -> TransferRowMapping.bind(ps, t) }
        }
        if (interactions.isNotEmpty()) {
            val rows = interactions.distinctBy { it.walletAddress to it.contractAddress }
            jdbc.batchUpdate(INTERACTION_INSERT, rows, rows.size) { ps, i ->
                TransferRowMapping.bind(ps, i)
            }
        }
    }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM $TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update("DELETE FROM $INTERACTION_TABLE WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE $TABLE, $INTERACTION_TABLE")
    }

    companion object {
        private val INSERT =
            "INSERT INTO $TABLE (id, " +
                COLUMNS.joinToString() +
                ") VALUES (?, " +
                COLUMNS.joinToString {
                    if (it == "event_type") "CAST(? AS transfers.event_type)" else "?"
                } +
                ") ON CONFLICT (id) DO NOTHING"

        private val INTERACTION_INSERT =
            "INSERT INTO $INTERACTION_TABLE (" +
                INTERACTION_COLUMNS.joinToString() +
                ") VALUES (" +
                INTERACTION_COLUMNS.joinToString { "?" } +
                ") ON CONFLICT DO NOTHING"
    }
}
