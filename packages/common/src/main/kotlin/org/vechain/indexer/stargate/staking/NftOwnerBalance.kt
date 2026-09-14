package org.vechain.indexer.stargate.staking

import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.stargate.token.TokenLevel

/**
 * How many Stargate NFTs [owner] holds after [blockNumber]; a holder is an owner with total > 0.
 */
data class NftOwnerBalance(
    val owner: String,
    val total: Long,
    val byLevel: Map<TokenLevel, Long>,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument
