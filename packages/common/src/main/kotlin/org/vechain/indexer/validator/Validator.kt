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
import org.vechain.indexer.stargate.token.TokenLevel

@Document(collection = IndexerNames.VALIDATOR.COLLECTION)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Validator(
    @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val endorser: String? = null, // address of the endorser
    val beneficiary: String? = null,
    val status: Status? = null, // active, inactive, jailed, etc.
    @Field(targetType = FieldType.DECIMAL128)
    val vetStaked: BigDecimal? = null, // amount of VET staked
    @Field(targetType = FieldType.DECIMAL128) val validatorVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val delegatorVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val queuedVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorQueuedVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val delegatorQueuedVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorExitingVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val delegatorExitingVetStaked: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val exitingVetStaked: BigDecimal? = null,
    @JsonIgnore
    val exitingValidatorVetStaked: BigDecimal =
        BigDecimal.ZERO, // amount of VET in the process of exiting
    val cycleEndBlock: Long? = null, // end block of the current cycle
    @Field(targetType = FieldType.DECIMAL128)
    val totalRewards: BigDecimal? = null, // total rewards earned
    @Field(targetType = FieldType.DECIMAL128) val blockProbability: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val blocksPerEpoch: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalTvl: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorTvl: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val delegatorTvl: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorTvlPercentage: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val tvlBasedYield: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val validatorYield: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val avgDelegatorYield: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val nextCycleTvlBasedYield: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val nextCycleValidatorYield: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val nextCycleAvgDelegatorYield: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val nftYieldsNextCycle: Map<TokenLevel, BigDecimal>? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalWeight: BigDecimal? = null,
    val online: Boolean? = null,
    val completedPeriods: Long? = null,
    val startBlock: Long? = null,
    val cyclePeriodLength: Long? = null,
    @Field(targetType = FieldType.DECIMAL128) val blocksPerYear: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128) val percentageOffline: BigDecimal? = null,
    val offlineBlocks: Long? = null,
    val exitBlock: Long? = null, // Block at which validator will exit (for EXITING status)
    val queuePosition: Long? = null, // Position in queue (1-based), null if not QUEUED
    val availableStartBlock: Long? = null, // Block at which queued validator can become active
    @JsonIgnore override val version: Int = 0,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    /**
     * Check if this Validator is equivalent to another, ignoring volatile fields such as blockId,
     * blockNumber, blockTimestamp, and version.
     */
    fun isEquivalentTo(other: Validator): Boolean =
        this.copy(
            blockId = other.blockId,
            blockNumber = other.blockNumber,
            blockTimestamp = other.blockTimestamp,
            version = other.version,
        ) ==
            other.copy(
                blockId = this.blockId,
                blockNumber = this.blockNumber,
                blockTimestamp = this.blockTimestamp,
                version = this.version,
            )
}
