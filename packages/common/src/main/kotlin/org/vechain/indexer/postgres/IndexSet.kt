package org.vechain.indexer.postgres

import org.vechain.indexer.b3tr.action.ActionIndexes
import org.vechain.indexer.blocks.BlocksIndexes
import org.vechain.indexer.history.HistoryIndexes
import org.vechain.indexer.stargate.staking.StargateStakingIndexes
import org.vechain.indexer.stargate.token.StargateTokenIndexes
import org.vechain.indexer.stargate.tokenReward.TokenRewardIndexes
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedIndexes
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedIndexes
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedIndexes
import org.vechain.indexer.transfer.TransferIndexes

/** An index only `packages/api` reads, so a backfill can do without it until the head. */
data class DeferrableIndex(
    val name: String,
    val table: String,
    val definition: String,
    /** Returns as a unique index the builder then relabels; nothing may reference it. */
    val primaryKey: Boolean = false,
)

/** One schema's deferrable indexes: the truth for what a schema carries when it is serving. */
data class IndexSet(val schema: String, val indexes: List<DeferrableIndex>)

/** Every declared set, so a test database can stand a schema up the way a served one stands. */
object IndexSets {
    val ALL: List<IndexSet> =
        listOf(
            HistoryIndexes.SET,
            ActionIndexes.SET,
            BlocksIndexes.SET,
            TransferIndexes.SET,
            StargateTokenIndexes.SET,
            TokenRewardIndexes.SET,
            VetDelegatedIndexes.SET,
            VthoGeneratedIndexes.SET,
            VthoClaimedIndexes.SET,
            StargateStakingIndexes.SET,
        )
}
