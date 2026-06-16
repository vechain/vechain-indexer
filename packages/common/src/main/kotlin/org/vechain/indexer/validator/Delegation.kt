package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigInteger
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.stargate.token.TokenLevel

/**
 * Indexed delegation state — V2.
 *
 * Mirrors the V1 [Delegation] document shape so the API layer can keep the same response surface,
 * but is built from events alone — no chain reads, no aggregator dependency.
 *
 * ### Differences from V1 [Delegation]
 * - Drops `validatorCycleLength` — always derivable from `Validator.stakingPeriodLength`.
 * - Renames `validatorNextCycle` → [transitionAtBlock]. Same semantics (block at which the next
 *   scheduled status flip is due) but the value is computed from `Validator` reads, not from chain
 *   inspections. `null` means "no scheduled transition" (either zero-cycle delegation whose
 *   validator hasn't started, or a terminal [DelegationStatus.EXITED]).
 * - [status] uses [DelegationStatus] (no `NONE` — a delegation always has a definite state) to keep
 *   V2 isolated from V1's [Status] enum.
 *
 * The V1 [Delegation.totalRewardsClaimed] semantics carry over unchanged.
 */
@Document(collection = IndexerNames.DELEGATION.COLLECTION)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Delegation(
    @Id val id: String,
    val validator: String,
    val tokenId: String,
    val owner: String,
    val status: DelegationStatus,
    val tokenLevel: TokenLevel,
    val stakedAmount: String,
    val totalRewardsClaimed: BigInteger,
    @JsonIgnore val txId: String,
    /**
     * Block at which the next scheduled status transition is due (QUEUED→ACTIVE or EXITING→EXITED).
     * `null` when no transition is scheduled — either the bound validator hasn't been activated yet
     * (zero-cycle), or the delegation is in a terminal state.
     */
    @JsonIgnore val transitionAtBlock: Long? = null,
    /**
     * Block at which `DelegationInitiated` fired for this row — the chain's authoritative
     * initiation timestamp. Set once on creation and never updated thereafter. Distinct from
     * [blockNumber], which is versioned-document metadata that re-stamps to the current block on
     * every persisted change (e.g. owner transfer, rewards claim). Cycle-boundary math anchored on
     * [blockNumber] would drift across such updates; this field is the stable anchor.
     *
     * Nullable for backward compatibility with rows persisted before this field existed — consumers
     * must fall back to [blockNumber] when null. New rows always populate it.
     */
    @JsonIgnore val initiatedAtBlock: Long? = null,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore override val version: Int = 1,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
