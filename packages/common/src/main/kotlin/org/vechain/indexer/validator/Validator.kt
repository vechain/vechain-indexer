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
 * **Not yet wired up:** `totalRewards` (the reward ledger lives in the separate `validator-reward`
 * profile).
 */
@Document(collection = IndexerNames.VALIDATOR.COLLECTION)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Validator(
    @Id val id: String,
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
    @Field(targetType = FieldType.DECIMAL128) val validatorVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorLockedWeight: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val delegatorVetStaked: BigDecimal? = null,
    // Persisted sum of validatorVetStaked + delegatorVetStaked. Stored so V1's deprecated
    // `sortBy=totalTvl` can map to a Mongo-sortable field (TVL preserves stake order within
    // a single request since vetPrice is a per-request scalar).
    @Field(targetType = FieldType.DECIMAL128) val vetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorQueuedVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val queuedVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val exitingVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorExitingVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalNextPeriodWeight: BigDecimal? = null,
    val queuePosition: Long? = null,
    val availableStartBlock: Long? = null,
    val scheduledSlots: Long = 0,
    val proposedBlocks: Long = 0,
    val missedSlots: Long = 0,
    val lastProposedBlockNumber: Long? = null,
    val lastMissedBlockNumber: Long? = null,
    @JsonIgnore override val version: Int = 0,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
