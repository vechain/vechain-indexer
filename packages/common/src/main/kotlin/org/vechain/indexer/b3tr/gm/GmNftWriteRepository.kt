package org.vechain.indexer.b3tr.gm

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.gm.GmNftRowMapping.COLUMNS
import org.vechain.indexer.b3tr.gm.GmNftRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `b3tr_gm.state` as a temporal table keyed on the GM token id. */
@Repository
@ConditionalOnPostgres
open class GmNftWriteRepository(@Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate) :
    PostgresIndexerTables {

    /** Applies the tokens block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(nfts: List<GmNft>) {
        nfts.groupBy { it.blockNumber }.toSortedMap().forEach(::saveBlock)
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveBlock(blockNumber: Long, block: List<GmNft>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { it.tokenId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? WHERE token_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, nft ->
            ps.setLong(1, blockNumber)
            ps.setBigDecimal(2, BigDecimal(nft.tokenId))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, nft -> GmNftRowMapping.bind(ps, nft) }
    }

    /** The current row of each of [tokenIds], which the events in a batch carry forward. */
    open fun findCurrentByTokenIds(tokenIds: Set<String>): List<GmNft> =
        jdbc.query(
            "SELECT * FROM $TABLE WHERE token_id = ANY(?) AND superseded_at IS NULL",
            { rs, _ -> GmNftRowMapping.read(rs) },
            tokenIds.map(::BigDecimal).toTypedArray(),
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
            "INSERT INTO $TABLE (token_id, block_number, " +
                COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                COLUMNS.joinToString { if (it == "level") "CAST(? AS b3tr_gm.level)" else "?" } +
                ") ON CONFLICT (token_id, block_number) DO UPDATE SET " +
                COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
