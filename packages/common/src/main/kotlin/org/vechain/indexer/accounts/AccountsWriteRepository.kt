package org.vechain.indexer.accounts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** Everything one block adds to the `accounts` schema. */
data class AccountsUpdate(
    val blockNumber: Long,
    val overviews: List<AccountOverview> = emptyList(),
    val balances: List<VetBalance> = emptyList(),
    val newAccounts: List<String> = emptyList(),
    val totals: AccountTotalsSeries? = null,
)

/** The `accounts` schema: the temporal overview, the balance changelog, the count and its set. */
@Repository
@ConditionalOnPostgres
open class AccountsWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: AccountsUpdate) {
        saveOverviews(update.blockNumber, update.overviews)
        batch(VET_BALANCE_INSERT, update.balances) { ps, b -> VetBalanceRowMapping.bind(ps, b) }
        batch(SEEN_INSERT, update.newAccounts) { ps, address ->
            ps.setBytes(1, bytes(address))
            ps.setLong(2, update.blockNumber)
        }
        update.totals?.let { t ->
            jdbc.update(TOTALS_INSERT) { ps -> AccountTotalsRowMapping.bind(ps, t) }
        }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveOverviews(blockNumber: Long, overviews: List<AccountOverview>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = overviews.associateBy { it.address }.values.toList()
        batch(
            "UPDATE ${AccountOverviewRowMapping.TABLE} SET superseded_at = ? WHERE address = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
        ) { ps, a ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(a.address))
            ps.setLong(3, blockNumber)
        }
        batch(OVERVIEW_INSERT, rows) { ps, a -> AccountOverviewRowMapping.bind(ps, a) }
    }

    /** The genesis allocations as the schema's first rows, before any block is processed. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun saveGenesis(overviews: List<AccountOverview>, balances: List<VetBalance>) {
        batch(OVERVIEW_INSERT, overviews) { ps, a -> AccountOverviewRowMapping.bind(ps, a) }
        batch(VET_BALANCE_INSERT, balances) { ps, b -> VetBalanceRowMapping.bind(ps, b) }
    }

    open fun hasOverviews(): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM ${AccountOverviewRowMapping.TABLE})",
            Boolean::class.java,
        )!!

    /**
     * Credits every current holder the passive VTHO accrued up to the Hayabusa block, closing the
     * generation there. Each settled account gets a row at the block; returns how many.
     */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun settlePassiveVtho(blockId: String, blockNumber: Long, blockTimestamp: Long): Int {
        val t = AccountOverviewRowMapping.TABLE
        val due =
            "superseded_at IS NULL AND block_number < ? AND vet_balance > 0 " +
                "AND last_vtho_settlement IS NOT NULL AND last_vtho_settlement < ?"
        jdbc.update(
            "INSERT INTO $t (address, block_number, " +
                AccountOverviewRowMapping.COLUMNS.joinToString() +
                ") SELECT address, ?, ?, ?, first_seen, ?, transactions_sent, clauses_sent, " +
                "vtho_burned, vtho_delegated, gas_used, vet_sent, vet_received, vet_balance, " +
                "vtho_block_rewards, vtho_passive_generation + " +
                "div(vet_balance * (? - last_vtho_settlement) * 5, 1000000000), ? " +
                "FROM $t WHERE $due",
            blockNumber,
            bytes(blockId),
            blockTimestamp,
            blockTimestamp,
            blockTimestamp,
            blockTimestamp,
            blockNumber,
            blockTimestamp,
        )
        return jdbc.update(
            "UPDATE $t SET superseded_at = ? WHERE $due",
            blockNumber,
            blockNumber,
            blockTimestamp,
        )
    }

    /** The current overview of each of [addresses], which the block's activity moves. */
    open fun findCurrentOverviews(addresses: Set<String>): List<AccountOverview> =
        if (addresses.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM ${AccountOverviewRowMapping.TABLE} WHERE address = ANY(?) " +
                    "AND superseded_at IS NULL",
                { rs, _ -> AccountOverviewRowMapping.read(rs) },
                addresses.map(::bytes).toTypedArray(),
            )

    /** Which of [addresses] the count already includes. */
    open fun findSeen(addresses: Set<String>): Set<String> =
        if (addresses.isEmpty()) emptySet()
        else
            jdbc
                .query(
                    "SELECT address FROM $SEEN_TABLE WHERE address = ANY(?)",
                    { rs, _ -> hex(rs.getBytes(1)) },
                    addresses.map(::bytes).toTypedArray(),
                )
                .toSet()

    /** The newest totals row below [blockNumber], which the block's new accounts add to. */
    open fun findTotalsBefore(blockNumber: Long): AccountTotalsSeries? =
        jdbc
            .query(
                "SELECT * FROM ${AccountTotalsRowMapping.TABLE} WHERE block_number < ? " +
                    "ORDER BY block_number DESC LIMIT 1",
                { rs, _ -> AccountTotalsRowMapping.read(rs) },
                blockNumber,
            )
            .firstOrNull()

    override fun rollbackFrom(blockNumber: Long) {
        TABLES.forEach { jdbc.update("DELETE FROM $it WHERE block_number >= ?", blockNumber) }
        jdbc.update(
            "UPDATE ${AccountOverviewRowMapping.TABLE} SET superseded_at = NULL " +
                "WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE ${TABLES.joinToString()}")
    }

    override fun prune(before: Long): Int =
        jdbc.update(
            "DELETE FROM ${AccountOverviewRowMapping.TABLE} WHERE superseded_at < ?",
            before,
        )

    private fun <T> batch(
        sql: String,
        rows: List<T>,
        bind: (java.sql.PreparedStatement, T) -> Unit,
    ) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(sql, rows, rows.size) { ps, row -> bind(ps, row) }
    }

    companion object {
        private const val SEEN_TABLE = "accounts.seen"

        private val TABLES =
            listOf(
                AccountOverviewRowMapping.TABLE,
                VetBalanceRowMapping.TABLE,
                AccountTotalsRowMapping.TABLE,
                SEEN_TABLE,
            )

        private fun upsert(table: String, key: List<String>, columns: List<String>): String =
            "INSERT INTO $table (${(key + columns).joinToString()}) " +
                "VALUES (${(key + columns).joinToString { "?" }}) " +
                "ON CONFLICT (${key.joinToString()}) DO UPDATE SET " +
                columns.joinToString { "$it = EXCLUDED.$it" }

        private val OVERVIEW_INSERT =
            upsert(
                AccountOverviewRowMapping.TABLE,
                listOf("address", "block_number"),
                AccountOverviewRowMapping.COLUMNS,
            )

        private val VET_BALANCE_INSERT =
            upsert(
                VetBalanceRowMapping.TABLE,
                listOf("address", "block_timestamp"),
                VetBalanceRowMapping.COLUMNS,
            )

        private val TOTALS_INSERT =
            upsert(
                AccountTotalsRowMapping.TABLE,
                listOf("block_number"),
                AccountTotalsRowMapping.COLUMNS,
            )

        private const val SEEN_INSERT =
            "INSERT INTO $SEEN_TABLE (address, block_number) VALUES (?, ?) " +
                "ON CONFLICT (address) DO NOTHING"
    }
}
