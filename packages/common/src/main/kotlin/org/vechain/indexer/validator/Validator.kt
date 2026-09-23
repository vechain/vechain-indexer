package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import org.vechain.indexer.IndexedDocument

/**
 * Indexed validator state. Persists only what the chain (built-in Staker) and PoS-schedule
 * observation provide directly; derived and price-dependent fields are computed at API read time.
 *
 * **Derivable at the API/projection layer, NOT stored:**
 * - `delegatorQueuedVetStaked` = `queuedVetStaked - validatorQueuedVetStaked`
 * - `delegatorExitingVetStaked` = `exitingVetStaked - validatorExitingVetStaked`
 * - `cycleEndBlock` = `startBlock + (completedPeriods + 1) * cyclePeriodLength`
 * - `totalWeight` = sum of `validatorLockedWeight` across the active set (chain aggregate)
 * - `blockProbability` = `validatorLockedWeight / totalWeight`
 * - `blocksPerEpoch` = constant (180)
 * - `blocksPerYear` = constant (3,155,760)
 *
 * **Price-/oracle-dependent, fetched at API read time:** TVL, current- and next-cycle yields, NFT
 * yields. Requires VET/VTHO USD prices from `PriceFeedOracle` (network-specific contract). Keeping
 * these out of the indexer lets it run unchanged on mainnet, testnet, solo, and custom networks.
 *
 * **Not yet wired up:** `totalRewards` (the reward ledger lives in `validator.slot`).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Validator(
    val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val endorser: String? = null,
    val beneficiary: String? = null,
    val status: Status? = null,
    val cyclePeriodLength: Long? = null,
    val startBlock: Long? = null,
    val exitBlock: Long? = null,
    val completedPeriods: Long? = null,
    val validatorVetStaked: BigDecimal? = null,
    val validatorLockedWeight: BigDecimal? = null,
    val delegatorVetStaked: BigDecimal? = null,
    // Persisted sum of validatorVetStaked + delegatorVetStaked, so V1's deprecated
    // `sortBy=totalTvl` has a column to sort on (a per-request vetPrice preserves stake order).
    val vetStaked: BigDecimal? = null,
    val validatorQueuedVetStaked: BigDecimal? = null,
    val queuedVetStaked: BigDecimal? = null,
    val exitingVetStaked: BigDecimal? = null,
    val validatorExitingVetStaked: BigDecimal? = null,
    val totalNextPeriodWeight: BigDecimal? = null,
    val queuePosition: Long? = null,
    val availableStartBlock: Long? = null,
    val scheduledSlots: Long = 0,
    val proposedBlocks: Long = 0,
    val missedSlots: Long = 0,
    val lastProposedBlockNumber: Long? = null,
    val lastMissedBlockNumber: Long? = null,
    // Mirror of the chain's `getValidation().offlineBlock`: the block at which the validator was
    // last marked offline by thor (cleared when they next sign a block). Used as the baseline for
    // detecting new misses — we increment `missedSlots` when chain `offlineBlock` advances past
    // the value stored here.
    val offlineBlock: Long? = null,
) : IndexedDocument
