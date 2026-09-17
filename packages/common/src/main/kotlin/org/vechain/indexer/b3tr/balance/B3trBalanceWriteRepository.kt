package org.vechain.indexer.b3tr.balance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.balance.B3trBalanceRowMapping.COLUMNS
import org.vechain.indexer.b3tr.balance.B3trBalanceRowMapping.TABLE
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `b3tr_balance.state` as a temporal table keyed on the holder's address. */
@Repository
@ConditionalOnPostgres
open class B3trBalanceWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the balances block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(balances: List<B3trBalance>) {
        balances.groupBy { it.blockNumber }.toSortedMap().forEach(::saveBlock)
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveBlock(blockNumber: Long, block: List<B3trBalance>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { it.address }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? WHERE address = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, b ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(b.address))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, b -> B3trBalanceRowMapping.bind(ps, b) }
    }

    /** Each of [addresses]' balance as it stood before [block], so a replayed block starts over. */
    open fun findCurrentByAddresses(addresses: Set<String>, block: Long): List<B3trBalance> =
        if (addresses.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $TABLE WHERE address = ANY(?) AND block_number < ? " +
                    "AND (superseded_at IS NULL OR superseded_at >= ?)",
                { rs, _ -> B3trBalanceRowMapping.read(rs) },
                addresses.map(::bytes).toTypedArray(),
                block,
                block,
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
            "INSERT INTO $TABLE (address, block_number, " +
                COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (address, block_number) DO UPDATE SET " +
                COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
