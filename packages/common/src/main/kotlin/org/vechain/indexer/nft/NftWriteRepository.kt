package org.vechain.indexer.nft

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `nft.ownership` as a temporal table; see [NftBlacklistWriteRepository] for the pattern. */
@Repository
@ConditionalOnPostgres
open class NftWriteRepository(@Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate) :
    PostgresIndexerTables {

    /** Applies the transfers block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(nfts: List<IndexedNft>) {
        nfts
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, block) ->
                saveBlock(blockNumber, block.map(NftRowMapping::flatten))
            }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveBlock(blockNumber: Long, block: List<NftRow>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { it.id.toList() }.values.toList()
        jdbc.batchUpdate(
            "UPDATE nft.ownership SET superseded_at = ? WHERE contract_address = ? AND token_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, r ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, r.contractAddress)
            ps.setBigDecimal(3, r.tokenId)
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(
            """
            INSERT INTO nft.ownership
              (contract_address, token_id, block_number, id, owner, tx_id, block_id, block_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (contract_address, token_id, block_number) DO UPDATE SET
              owner = EXCLUDED.owner, tx_id = EXCLUDED.tx_id, block_id = EXCLUDED.block_id,
              block_timestamp = EXCLUDED.block_timestamp
            """
                .trimIndent(),
            rows,
            rows.size,
        ) { ps, r ->
            ps.setBytes(1, r.contractAddress)
            ps.setBigDecimal(2, r.tokenId)
            ps.setLong(3, r.blockNumber)
            ps.setBytes(4, r.id)
            ps.setBytes(5, r.owner)
            ps.setBytes(6, r.txId)
            ps.setBytes(7, r.blockId)
            ps.setLong(8, r.blockTimestamp)
        }
    }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM nft.ownership WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE nft.ownership SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE nft.ownership")
    }

    override fun prune(before: Long) {
        jdbc.update("DELETE FROM nft.ownership WHERE superseded_at < ?", before)
    }
}
