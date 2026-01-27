package org.vechain.indexer.e2e

import org.jetbrains.annotations.TestOnly
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.transfer.IndexedTransferEvent
import org.vechain.indexer.transfer.TransferEventType

@Profile("e2e")
@Service
open class E2EService(private val jdbcTemplate: JdbcTemplate) {

    @TestOnly
    open fun getNftTransfers(): List<IndexedTransferEvent> {
        return jdbcTemplate.query(
            """
            SELECT * FROM transfer_events
            WHERE event_type = ?
            ORDER BY block_number DESC
            """
                .trimIndent(),
            { rs, _ ->
                IndexedTransferEvent(
                    id = rs.getString("id"),
                    blockId = rs.getString("block_id"),
                    blockNumber = rs.getLong("block_number"),
                    blockTimestamp = rs.getLong("block_timestamp"),
                    txId = rs.getString("tx_id"),
                    from = rs.getString("from_address"),
                    to = rs.getString("to_address"),
                    value = rs.getString("value"),
                    tokenAddress = rs.getString("token_address"),
                    tokenId = rs.getString("token_id"),
                    topics = emptyList(), // Simplified for E2E
                    eventType = TransferEventType.valueOf(rs.getString("event_type")),
                )
            },
            TransferEventType.NFT.name,
        )
    }

    @TestOnly
    open fun getNfts(): List<IndexedNft> {
        return jdbcTemplate.query(
            """
            SELECT * FROM nfts
            WHERE is_current = true
            ORDER BY block_number DESC
            """
                .trimIndent()
        ) { rs, _ ->
            IndexedNft(
                id = rs.getString("entity_id"),
                version = rs.getInt("version"),
                blockId = rs.getString("block_id"),
                blockNumber = rs.getLong("block_number"),
                blockTimestamp = rs.getLong("block_timestamp"),
                tokenId = rs.getString("token_id"),
                contractAddress = rs.getString("contract_address"),
                owner = rs.getString("owner"),
                txId = rs.getString("tx_id"),
                isBlacklisted = rs.getObject("is_blacklisted") as? Boolean,
            )
        }
    }
}
