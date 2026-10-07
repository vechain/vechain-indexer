package org.vechain.indexer.wov.marketplace

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables

/** The `wov_marketplace` schema: sales, the terms they settle on, running totals, progress. */
@Repository
@ConditionalOnPostgres
open class WovMarketplaceWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Everything one entry adds, written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(entry: WovMarketplaceEntry) {
        insert(TERMS_INSERT, entry.terms.distinctBy { it.key }, WovTermsRowMapping::bind)
        insert(SALE_INSERT, entry.sales.distinctBy { it.id }, WovSaleRowMapping::bind)
        insert(RUNNING_INSERT, entry.running, WovBuyerRunningRowMapping::bind)
        insert(BUYER_INSERT, entry.buyers.distinctBy { it.address }, WovBuyerRowMapping::bind)
        jdbc.update(PROGRESS_INSERT, entry.endBlockNumber, entry.endBlockTimestamp)
    }

    private fun <T> insert(
        sql: String,
        rows: List<T>,
        bind: (java.sql.PreparedStatement, T) -> Unit,
    ) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(sql, rows, rows.size) { ps, row -> bind(ps, row) }
    }

    /** The announced terms of each [keys] entry, which a purchase or settlement looks up. */
    open fun findTerms(keys: Collection<WovTermsKey>): List<WovSaleTerms> {
        if (keys.isEmpty()) return emptyList()
        val values = keys.joinToString { "(?::bytea, ?::numeric)" }
        return jdbc.query(
            "SELECT t.* FROM (VALUES $values) k(contract_address, market_id) " +
                "JOIN ${WovTermsRowMapping.TABLE} t USING (contract_address, market_id)",
            { rs, _ -> WovTermsRowMapping.read(rs) },
            *keys
                .flatMap { listOf(bytes(it.contractAddress), BigDecimal(it.marketId)) }
                .toTypedArray(),
        )
    }

    /** Each pair's newest row before [blockNumber]: a retried entry seeds from before itself. */
    open fun findRunningBefore(
        pairs: Collection<Pair<String, String>>,
        blockNumber: Long,
    ): List<WovBuyerRunning> {
        if (pairs.isEmpty()) return emptyList()
        val values = pairs.joinToString { "(?::bytea, ?::bytea)" }
        return jdbc.query(
            "SELECT r.* FROM (VALUES $values) k(buyer, payment_token) CROSS JOIN LATERAL (" +
                "SELECT * FROM $RUNNING_TABLE r WHERE r.buyer = k.buyer " +
                "AND r.payment_token = k.payment_token AND r.block_number < ? " +
                "ORDER BY r.block_number DESC LIMIT 1) r",
            { rs, _ -> WovBuyerRunningRowMapping.read(rs) },
            *(pairs.flatMap { listOf(bytes(it.first), bytes(it.second)) } + blockNumber)
                .toTypedArray(),
        )
    }

    override fun rollbackFrom(blockNumber: Long) {
        BY_BLOCK.forEach { jdbc.update("DELETE FROM $it WHERE block_number >= ?", blockNumber) }
        jdbc.update(
            "DELETE FROM ${WovBuyerRowMapping.TABLE} WHERE first_block_number >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE ${(BY_BLOCK + WovBuyerRowMapping.TABLE).joinToString()}")
    }

    /** Only the progress marker is superseded; the newest row is at or past [before]. */
    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM $PROGRESS_TABLE WHERE block_number < ?", before)

    companion object {
        private const val RUNNING_TABLE = WovBuyerRunningRowMapping.TABLE
        private const val PROGRESS_TABLE = "wov_marketplace.progress"

        private val BY_BLOCK =
            listOf(WovSaleRowMapping.TABLE, WovTermsRowMapping.TABLE, RUNNING_TABLE, PROGRESS_TABLE)

        private val SALE_INSERT =
            "INSERT INTO ${WovSaleRowMapping.TABLE} (id, ${WovSaleRowMapping.COLUMNS.joinToString()}" +
                ") VALUES (?, " +
                WovSaleRowMapping.COLUMNS.joinToString {
                    if (it == "mechanism") "CAST(? AS wov_marketplace.mechanism)" else "?"
                } +
                ") ON CONFLICT (id) DO NOTHING"

        // A market id is never reused, so a repeat is the same listing seen again.
        private val TERMS_INSERT =
            "INSERT INTO ${WovTermsRowMapping.TABLE} (${WovTermsRowMapping.COLUMNS.joinToString()}" +
                ") VALUES (${WovTermsRowMapping.COLUMNS.joinToString { "?" }}) " +
                "ON CONFLICT (contract_address, market_id) DO NOTHING"

        private val RUNNING_INSERT =
            "INSERT INTO $RUNNING_TABLE (${WovBuyerRunningRowMapping.COLUMNS.joinToString()}" +
                ") VALUES (${WovBuyerRunningRowMapping.COLUMNS.joinToString { "?" }})"

        private val BUYER_INSERT =
            "INSERT INTO ${WovBuyerRowMapping.TABLE} (${WovBuyerRowMapping.COLUMNS.joinToString()}" +
                ") VALUES (${WovBuyerRowMapping.COLUMNS.joinToString { "?" }}) " +
                "ON CONFLICT (address) DO NOTHING"

        private const val PROGRESS_INSERT =
            "INSERT INTO $PROGRESS_TABLE (block_number, block_timestamp) VALUES (?, ?) " +
                "ON CONFLICT (block_number) DO UPDATE SET block_timestamp = EXCLUDED.block_timestamp"
    }
}
