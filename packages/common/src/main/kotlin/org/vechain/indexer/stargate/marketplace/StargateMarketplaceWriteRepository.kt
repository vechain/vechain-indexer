package org.vechain.indexer.stargate.marketplace

import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `stargate_marketplace.listing` as a temporal table, plus the indexer's read of current rows. */
@Repository
@ConditionalOnPostgres
open class StargateMarketplaceWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the rows block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(listings: List<StargateListing>) {
        listings
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, rows) -> saveBlock(blockNumber, rows) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows.
    private fun saveBlock(blockNumber: Long, listings: List<StargateListing>) {
        // A sale listed and settled in one block keeps the row it ended the block in.
        val rows = listings.associateBy { it.saleId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? " +
                "WHERE sale_id = ? AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, l ->
            ps.setLong(1, blockNumber)
            ps.setBigDecimal(2, BigDecimal(l.saleId))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, l ->
            StargateListingRowMapping.bind(ps, l)
        }
    }

    /** The current row of each of [saleIds]: where a purchase takes its seller from. */
    open fun findCurrent(saleIds: Collection<BigInteger>): List<StargateListing> {
        if (saleIds.isEmpty()) return emptyList()
        return jdbc.query(
            "$CURRENT AND sale_id = ANY(?) ORDER BY sale_id",
            { rs, _ -> StargateListingRowMapping.read(rs) },
            saleIds.map(::BigDecimal).toTypedArray(),
        )
    }

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
        private const val TABLE = StargateListingRowMapping.TABLE
        private const val CURRENT = "SELECT * FROM $TABLE WHERE superseded_at IS NULL"
        private val INSERT =
            "INSERT INTO $TABLE (sale_id, block_number, " +
                StargateListingRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                StargateListingRowMapping.COLUMNS.joinToString {
                    if (it == "status") "CAST(? AS stargate_marketplace.status)" else "?"
                } +
                ") ON CONFLICT (sale_id, block_number) DO UPDATE SET " +
                StargateListingRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
