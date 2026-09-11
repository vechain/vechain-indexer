package org.vechain.indexer.nft

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex

/** The current state per collection; the NFT and history readers anti-join this table inline. */
@Repository
@ConditionalOnPostgres
open class NftBlacklistReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun current(contractAddress: String): NftBlacklistState? =
        jdbc
            .query(
                "SELECT * FROM nft_blacklist.collection_state " +
                    "WHERE contract_address = ? AND superseded_at IS NULL",
                { rs, _ ->
                    NftBlacklistState(
                        contractAddress = PostgresHex.hex(rs.getBytes("contract_address")),
                        isBlacklisted = rs.getBoolean("is_blacklisted"),
                        blockId = PostgresHex.hex(rs.getBytes("block_id")),
                        blockNumber = rs.getLong("block_number"),
                        blockTimestamp = rs.getLong("block_timestamp"),
                    )
                },
                PostgresHex.bytes(contractAddress),
            )
            .firstOrNull()

    open fun blacklisted(): List<String> =
        jdbc.query(
            "SELECT contract_address FROM nft_blacklist.collection_state " +
                "WHERE superseded_at IS NULL AND is_blacklisted ORDER BY contract_address"
        ) { rs, _ ->
            PostgresHex.hex(rs.getBytes(1))
        }
}
