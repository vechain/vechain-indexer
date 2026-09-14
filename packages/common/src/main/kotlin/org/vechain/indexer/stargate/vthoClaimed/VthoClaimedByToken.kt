package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

/** One (account, token)'s running claim totals as of [blockNumber]. */
data class VthoClaimedByToken(
    val account: String,
    val tokenId: String,
    val legacyRewards: BigInteger,
    val delegationRewards: BigInteger,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
) : IndexedDocument

/** Claim totals summed over an account, or one of its tokens. */
data class VthoClaimedTotals(val legacyRewards: BigInteger, val delegationRewards: BigInteger) {
    val total: BigInteger
        get() = legacyRewards + delegationRewards
}
