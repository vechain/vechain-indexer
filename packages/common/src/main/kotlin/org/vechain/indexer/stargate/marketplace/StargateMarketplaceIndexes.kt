package org.vechain.indexer.stargate.marketplace

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** The API's open-listing lookups; the indexer reads a listing only by sale id, on the key. */
object StargateMarketplaceIndexes {

    val SET =
        IndexSet(
            "stargate_marketplace",
            listOf(
                DeferrableIndex(
                    "listing_open_idx",
                    "listing",
                    "(token_id) WHERE superseded_at IS NULL AND status = 'OPEN'",
                )
            ),
        )
}
