package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigDecimal
import org.bson.types.Decimal128
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Views

@Document(collection = "validators")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Validator(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val endorser: String? = null, // address of the endorser
    val beneficiary: String? = null,
    val status: Status? = null, // active, inactive, jailed, etc.
    val vetStaked: Decimal128? = null, // amount of VET staked
    val validatorVetStaked: Decimal128? = null,
    val delegatorVetStaked: Decimal128? = null,
    val queuedVetStaked: Decimal128? = null,
    val validatorQueuedVetStaked: Decimal128? = null,
    val delegatorQueuedVetStaked: Decimal128? = null,
    val validatorExitingVetStaked: Decimal128? = null,
    val delegatorExitingVetStaked: Decimal128? = null,
    val exitingVetStaked: Decimal128? = null,
    @JsonIgnore
    val exitingValidatorVetStaked: BigDecimal =
        BigDecimal.ZERO, // amount of VET in the process of exiting
    val cycleEndBlock: Long? = null, // end block of the current cycle
    val totalRewards: Decimal128? = null, // total rewards earned
    val blockProbability: Decimal128? = null,
    val blocksPerEpoch: Decimal128? = null,
    val totalTvl: Decimal128? = null,
    val validatorTvl: Decimal128? = null,
    val delegatorTvl: Decimal128? = null,
    val validatorTvlPercentage: Decimal128? = null,
    val tvlBasedYield: Decimal128? = null,
    val validatorYield: Decimal128? = null,
    val avgDelegatorYield: Decimal128? = null,
    val nextCycleTvlBasedYield: Decimal128? = null,
    val nextCycleValidatorYield: Decimal128? = null,
    val nextCycleAvgDelegatorYield: Decimal128? = null,
    val nftYieldsNextCycle: Map<TokenLevel, Decimal128>? = null,
    val totalWeight: Decimal128? = null,
    val online: Boolean? = null,
    val completedPeriods: Long? = null,
    val startBlock: Long? = null,
    val cyclePeriodLength: Long? = null,
    val blocksPerYear: Decimal128? = null,
    val percentageOffline: Decimal128? = null,
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

@Document("validators_archives")
@JsonView(Views.Public::class)
data class ValidatorArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: Validator) : Archive<Validator>
