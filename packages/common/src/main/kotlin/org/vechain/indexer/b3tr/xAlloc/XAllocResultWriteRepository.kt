package org.vechain.indexer.b3tr.xAlloc

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.xAlloc.XAllocResultRowMapping.COLUMNS
import org.vechain.indexer.b3tr.xAlloc.XAllocResultRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `b3tr_x_alloc.result` as a temporal table keyed on (round, app). */
@Repository
@ConditionalOnPostgres
open class XAllocResultWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the results block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(results: List<XAllocResult>) {
        results.groupBy { it.blockNumber }.toSortedMap().forEach(::saveBlock)
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveBlock(blockNumber: Long, block: List<XAllocResult>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { it.roundId to it.appId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? WHERE round_id = ? AND app_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, r ->
            ps.setLong(1, blockNumber)
            ps.setInt(2, r.roundId)
            ps.setBytes(3, bytes(r.appId))
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, r -> XAllocResultRowMapping.bind(ps, r) }
    }

    /**
     * The current row of every (round, app) pair the batch touches; the query widens to the cross
     * product of the two sets, whose extra rows the caller simply never looks up.
     */
    open fun findCurrent(roundIds: Set<Int>, appIds: Set<String>): List<XAllocResult> =
        if (roundIds.isEmpty() || appIds.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $TABLE WHERE round_id = ANY(?) AND app_id = ANY(?) " +
                    "AND superseded_at IS NULL",
                { rs, _ -> XAllocResultRowMapping.read(rs) },
                roundIds.toTypedArray(),
                appIds.map(::bytes).toTypedArray(),
            )

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM $TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update("UPDATE $TABLE SET superseded_at = NULL WHERE superseded_at >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE $TABLE")
    }

    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM $TABLE WHERE superseded_at < ?", before)

    companion object {
        private val INSERT =
            "INSERT INTO $TABLE (round_id, app_id, block_number, " +
                COLUMNS.joinToString() +
                ") VALUES (?, ?, ?, " +
                COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (round_id, app_id, block_number) DO UPDATE SET " +
                COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
