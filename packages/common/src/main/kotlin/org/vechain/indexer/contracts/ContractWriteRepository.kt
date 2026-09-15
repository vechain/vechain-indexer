package org.vechain.indexer.contracts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.contracts.ContractRowMapping.COLUMNS
import org.vechain.indexer.contracts.ContractRowMapping.TABLE
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `contracts.state` as a temporal table keyed on the contract address. */
@Repository
@ConditionalOnPostgres
open class ContractWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the contracts block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(contracts: List<Contract>) {
        contracts.groupBy { it.blockNumber }.toSortedMap().forEach(::saveBlock)
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveBlock(blockNumber: Long, block: List<Contract>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { it.address }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? WHERE address = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, c ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(c.address))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, c -> ContractRowMapping.bind(ps, c) }
    }

    /** The current row of each of [addresses], for the master updates in a batch of events. */
    open fun findCurrentByAddresses(addresses: Set<String>): List<Contract> =
        jdbc.query(
            "SELECT * FROM $TABLE WHERE address = ANY(?) AND superseded_at IS NULL",
            { rs, _ -> ContractRowMapping.read(rs) },
            addresses.map(::bytes).toTypedArray(),
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
