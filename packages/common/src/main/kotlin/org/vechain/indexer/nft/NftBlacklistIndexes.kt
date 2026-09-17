package org.vechain.indexer.nft

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The anti-join both NFT and history reads make; the indexer only ever writes here. */
object NftBlacklistIndexes {

    val SET =
        IndexSet(
            "nft_blacklist",
            listOf(
                DeferrableIndex(
                    "collection_state_flagged_idx",
                    "collection_state",
                    "(contract_address) WHERE superseded_at IS NULL AND is_blacklisted",
                )
            ),
        )
}
