package org.vechain.indexer.stargate.staking

import org.vechain.indexer.postgres.IndexSet
import org.vechain.indexer.timeseries.SeriesIndexes

/** Both series tables; the owner-balance changelog is the indexer's own and keeps its indexes. */
object StargateStakingIndexes {
    val SET =
        IndexSet(
            "stargate_staking",
            SeriesIndexes.of("vet_staked_by_block") + SeriesIndexes.of("nft_holders_by_block"),
        )
}
