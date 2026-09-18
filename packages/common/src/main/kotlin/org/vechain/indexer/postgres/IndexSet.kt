package org.vechain.indexer.postgres

import org.vechain.indexer.accounts.AccountsIndexes
import org.vechain.indexer.b3tr.action.ActionIndexes
import org.vechain.indexer.b3tr.balance.B3trBalanceIndexes
import org.vechain.indexer.b3tr.challenges.ChallengeIndexes
import org.vechain.indexer.b3tr.gm.GmNftIndexes
import org.vechain.indexer.b3tr.navigator.NavigatorIndexes
import org.vechain.indexer.b3tr.proposal.ProposalIndexes
import org.vechain.indexer.b3tr.treasury.TreasuryTransferIndexes
import org.vechain.indexer.b3tr.xAlloc.XAllocResultIndexes
import org.vechain.indexer.blocks.BlocksIndexes
import org.vechain.indexer.contracts.ContractIndexes
import org.vechain.indexer.explorer.ExplorerIndexes
import org.vechain.indexer.history.HistoryIndexes
import org.vechain.indexer.nft.NftBlacklistIndexes
import org.vechain.indexer.nft.NftIndexes
import org.vechain.indexer.safe.SafeIndexes
import org.vechain.indexer.stargate.staking.StargateStakingIndexes
import org.vechain.indexer.stargate.token.StargateTokenIndexes
import org.vechain.indexer.stargate.tokenReward.TokenRewardIndexes
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedIndexes
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedIndexes
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedIndexes
import org.vechain.indexer.transfer.TransferIndexes
import org.vechain.indexer.validator.DelegationIndexes
import org.vechain.indexer.validator.ValidatorBlockIndexes
import org.vechain.indexer.validator.ValidatorIndexes
import org.vechain.indexer.vevote.HistoricProposalsIndexes
import org.vechain.indexer.vevote.VeVoteIndexes

/** An index by name; in [IndexSet.indexes] one only `packages/api` reads, dropped in a backfill. */
data class DeferrableIndex(
    val name: String,
    val table: String,
    val definition: String,
    /** Returns as a unique index the builder then relabels; nothing may reference it. */
    val primaryKey: Boolean = false,
)

/** One schema's deferrable indexes: the truth for what a schema carries when it is serving. */
data class IndexSet(
    val schema: String,
    val indexes: List<DeferrableIndex>,
    /** The indexer's own, never dropped; too slow for a migration, they grow after start. */
    val needed: List<DeferrableIndex> = emptyList(),
)

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
            ValidatorIndexes.SET,
            ValidatorBlockIndexes.SET,
            DelegationIndexes.SET,
            B3trBalanceIndexes.SET,
            GmNftIndexes.SET,
            TreasuryTransferIndexes.SET,
            XAllocResultIndexes.SET,
            ChallengeIndexes.SET,
            NavigatorIndexes.SET,
            ProposalIndexes.SET,
            SafeIndexes.SET,
            AccountsIndexes.SET,
            ExplorerIndexes.SET,
            ContractIndexes.SET,
            NftIndexes.SET,
            NftBlacklistIndexes.SET,
            VeVoteIndexes.SET,
            HistoricProposalsIndexes.SET,
        )
}
