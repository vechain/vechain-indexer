package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByTokenRowMapping.TABLE
import org.vechain.indexer.timeseries.TimeFrameTable

/** The `stargate_vtho_claimed` schema: the claim series and the temporal per-token totals. */
@Repository
@ConditionalOnPostgres
open class VthoClaimedWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    private val series = TimeFrameTable(jdbc, VthoClaimedRowMapping)

    /** Writes the series and, block by block in ascending order, each token's new totals. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(byBlock: List<VthoClaimedByBlock>, byToken: List<VthoClaimedByToken>) {
        series.save(byBlock)
        byToken
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, rows) -> saveBlock(blockNumber, rows) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows.
    private fun saveBlock(blockNumber: Long, tokens: List<VthoClaimedByToken>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = tokens.associateBy { it.account to it.tokenId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? WHERE account = ? AND token_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, t ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(t.account))
            ps.setBigDecimal(3, BigDecimal(t.tokenId))
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, t ->
            VthoClaimedByTokenRowMapping.bind(ps, t)
        }
    }

    /** The newest series row, from which the service resumes its running totals. */
    open fun latest(): VthoClaimedByBlock? = series.latest()

    /** The current totals of every token of [accounts]. */
    open fun findCurrentByAccounts(accounts: Set<String>): List<VthoClaimedByToken> =
        jdbc.query(
            "SELECT * FROM $TABLE WHERE account = ANY(?) AND superseded_at IS NULL",
            { rs, _ -> VthoClaimedByTokenRowMapping.read(rs) },
            accounts.map(::bytes).toTypedArray(),
        )

    override fun rollbackFrom(blockNumber: Long) {
        series.rollbackFrom(blockNumber)
        jdbc.update("DELETE FROM $TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update("UPDATE $TABLE SET superseded_at = NULL WHERE superseded_at >= ?", blockNumber)
    }

    override fun truncate() {
        series.truncate()
        jdbc.execute("TRUNCATE $TABLE")
    }

    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM $TABLE WHERE superseded_at < ?", before)

    companion object {
        private val INSERT =
            "INSERT INTO $TABLE (account, token_id, block_number, " +
                VthoClaimedByTokenRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, ?, " +
                VthoClaimedByTokenRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (account, token_id, block_number) DO UPDATE SET " +
                VthoClaimedByTokenRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
