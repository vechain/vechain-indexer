package org.vechain.indexer.nft

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** Mutable state as a temporal table: close the open rows a block touches, insert the new ones. */
@Repository
@ConditionalOnPostgres
open class NftBlacklistWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the states block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(states: List<NftBlacklistState>) {
        states
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, rows) -> saveBlock(blockNumber, rows) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows.
    private fun saveBlock(blockNumber: Long, states: List<NftBlacklistState>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = states.associateBy { it.contractAddress }.values.toList()
        val contracts = rows.map { PostgresHex.bytes(it.contractAddress) }
        jdbc.update(
            "UPDATE nft_blacklist.collection_state SET superseded_at = ? " +
                "WHERE contract_address = ANY(?) AND superseded_at IS NULL AND block_number < ?",
            blockNumber,
            contracts.toTypedArray(),
            blockNumber,
        )
        jdbc.batchUpdate(
            """
            INSERT INTO nft_blacklist.collection_state
              (contract_address, block_number, block_id, block_timestamp, is_blacklisted)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (contract_address, block_number) DO UPDATE SET
              is_blacklisted = EXCLUDED.is_blacklisted, block_id = EXCLUDED.block_id,
              block_timestamp = EXCLUDED.block_timestamp
            """
                .trimIndent(),
            rows,
            rows.size,
        ) { ps, s ->
            ps.setBytes(1, PostgresHex.bytes(s.contractAddress))
            ps.setLong(2, s.blockNumber)
            ps.setBytes(3, PostgresHex.bytes(s.blockId))
            ps.setLong(4, s.blockTimestamp)
            ps.setBoolean(5, s.isBlacklisted)
        }
    }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update(
            "DELETE FROM nft_blacklist.collection_state WHERE block_number >= ?",
            blockNumber,
        )
        jdbc.update(
            "UPDATE nft_blacklist.collection_state SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE nft_blacklist.collection_state")
    }

    /** Safe while [before] is older than the deepest rollback the indexer will be asked for. */
    override fun prune(before: Long) {
        jdbc.update("DELETE FROM nft_blacklist.collection_state WHERE superseded_at < ?", before)
    }
}
