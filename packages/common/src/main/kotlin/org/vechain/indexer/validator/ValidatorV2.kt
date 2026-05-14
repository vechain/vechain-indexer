package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

/**
 * Indexed validator state — V2.
 *
 * Persists only what the chain (built-in Staker) and PoS schedule observation provide directly.
 * Field names match V1 [Validator] one-for-one so consumer migration is a straight swap of the
 * document type. The PoS-schedule liveness counters (`scheduledBlocks` / `proposedBlocks` /
 * `missedBlocks` / `lastProposedBlockNumber` / `lastMissedBlockNumber`) are the only entirely new
 * fields.
 *
 * ### Mapping from V1 [Validator]
 *
 * **Replaced by better V2 fields (drive V1's API surface from these):**
 * - V1 `offlineBlocks` → V2 `missedBlocks` (actually-missed PoS slots, not the transient chain
 *   `OfflineBlock` pointer).
 * - V1 `percentageOffline` → derive at API as `missedBlocks / scheduledBlocks`.
 * - V1 `online` → derive at API from `lastProposedBlockNumber` recency.
 *
 * **Derivable, NOT stored — compute at API/projection layer:**
 * - `vetStaked` = `validatorVetStaked + delegatorVetStaked`
 * - `delegatorQueuedVetStaked` = `queuedVetStaked - validatorQueuedVetStaked`
 * - `delegatorExitingVetStaked` = `exitingVetStaked - validatorExitingVetStaked`
 * - `cycleEndBlock` = `startBlock + (completedPeriods + 1) * cyclePeriodLength`
 * - `totalWeight` = sum of `validatorLockedWeight` across active set (chain aggregate)
 * - `blockProbability` = `validatorLockedWeight / totalWeight`
 * - `blocksPerEpoch` = constant (180)
 * - `blocksPerYear` = constant (3,155,760)
 *
 * **Out of scope for this indexer (price/oracle data — fetch at API read time):**
 * - `totalTvl`, `validatorTvl`, `delegatorTvl`, `validatorTvlPercentage`
 * - `tvlBasedYield`, `validatorYield`, `avgDelegatorYield`
 * - `nextCycleTvlBasedYield`, `nextCycleValidatorYield`, `nextCycleAvgDelegatorYield`
 * - `nftYieldsIfDelegatedNextCycle`, `nftYields`
 *
 * Requires VET/VTHO USD prices from `PriceFeedOracle` (network-specific contract) and Stargate
 * `getDelegatorsEffectiveStake`. Keeping these out lets the indexer run unchanged across mainnet,
 * testnet, solo, and custom networks. They could be reintroduced by calling those contracts
 * directly (not via the V1 `GetValidators` aggregator) and config-gating the calls per network.
 *
 * **Not yet wired up:**
 * - `totalRewards` — V1 declared this field but never populated it. The reward ledger lives in the
 *   separate `validator-reward` profile (per-block ledger). Decide whether to port that or
 *   aggregate at API time.
 */
@Document(collection = IndexerNames.VALIDATOR_V2.COLLECTION)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidatorV2(
    @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val endorser: String? = null,
    val beneficiary: String? = null,
    val status: StatusV2? = null,
    val cyclePeriodLength: Long? = null,
    val startBlock: Long? = null,
    val exitBlock: Long? = null,
    val completedPeriods: Long? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorLockedWeight: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val delegatorVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorQueuedVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val queuedVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val exitingVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorExitingVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalNextPeriodWeight: BigDecimal? = null,
    val queuePosition: Long? = null,
    val availableStartBlock: Long? = null,
    val scheduledBlocks: Long = 0,
    val proposedBlocks: Long = 0,
    val missedBlocks: Long = 0,
    val lastProposedBlockNumber: Long? = null,
    val lastMissedBlockNumber: Long? = null,
    @JsonIgnore override val version: Int = 0,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
