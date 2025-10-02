package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.bson.types.Decimal128
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
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
    val exitingVetStaked: Decimal128? = null,
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
    val totalWeight: Decimal128? = null,
    val online: Boolean? = null,
    val completedPeriods: Long? = null,
    val startBlock: Long? = null,
    val cyclePeriodLength: Long? = null,
    val blocksPerYear: Decimal128? = null,
    val percentageOffline: Decimal128? = null,
    val offlineBlocks: Long? = null,
    @JsonIgnore val totalVTHOSupply: Decimal128,
    @JsonIgnore override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    /**
     * Compares this Validator with another Validator instance to determine if they are equal. Two
     * Validator instances are considered equal if all their properties are equal.
     */
    fun equals(other: Validator): Boolean {
        return this.id == other.id &&
            this.endorser == other.endorser &&
            this.beneficiary == other.beneficiary &&
            this.status == other.status &&
            this.vetStaked.equals(other.vetStaked) &&
            this.validatorVetStaked.equals(other.validatorVetStaked) &&
            this.delegatorVetStaked.equals(other.delegatorVetStaked) &&
            this.queuedVetStaked.equals(other.queuedVetStaked) &&
            this.exitingVetStaked.equals(other.exitingVetStaked) &&
            this.cycleEndBlock == other.cycleEndBlock &&
            this.totalRewards.equals(other.totalRewards) &&
            this.blockProbability.equals(other.blockProbability) &&
            this.blocksPerEpoch.equals(other.blocksPerEpoch) &&
            this.totalTvl.equals(other.totalTvl) &&
            this.validatorTvl.equals(other.validatorTvl) &&
            this.delegatorTvl.equals(other.delegatorTvl) &&
            this.validatorTvlPercentage.equals(other.validatorTvlPercentage) &&
            this.tvlBasedYield.equals(other.tvlBasedYield) &&
            this.validatorYield.equals(other.validatorYield) &&
            this.avgDelegatorYield.equals(other.avgDelegatorYield) &&
            this.totalWeight.equals(other.totalWeight) &&
            this.online == other.online &&
            this.completedPeriods == other.completedPeriods &&
            this.startBlock == other.startBlock &&
            this.cyclePeriodLength == other.cyclePeriodLength &&
            this.blocksPerYear.equals(other.blocksPerYear) &&
            this.percentageOffline.equals(other.percentageOffline) &&
            this.offlineBlocks == other.offlineBlocks
        // TODO: Should this be compared? Its always different
        // this.totalVTHOSupply.compareTo(other.totalVTHOSupply) == 0
    }

    // TODO: remove this, just debugging rn
    fun printChanges(other: Validator) {
        if (this.id != other.id) {
            println("id changed from ${other.id} to ${this.id}")
        }
        if (this.endorser != other.endorser) {
            println("endorser changed from ${other.endorser} to ${this.endorser}")
        }
        if (this.beneficiary != other.beneficiary) {
            println("beneficiary changed from ${other.beneficiary} to ${this.beneficiary}")
        }
        if (this.status != other.status) {
            println("status changed from ${other.status} to ${this.status}")
        }
        if (!this.vetStaked.equals(other.vetStaked)) {
            println("vetStaked changed from ${other.vetStaked} to ${this.vetStaked}")
        }
        if (!this.validatorVetStaked.equals(other.validatorVetStaked)) {
            println(
                "validatorVetStaked changed from ${other.validatorVetStaked} to ${this.validatorVetStaked}"
            )
        }
        if (!this.delegatorVetStaked.equals(other.delegatorVetStaked)) {
            println(
                "delegatorVetStaked changed from ${other.delegatorVetStaked} to ${this.delegatorVetStaked}"
            )
        }

        if (!this.queuedVetStaked.equals(other.queuedVetStaked)) {
            println(
                "queuedVetStaked changed from ${other.queuedVetStaked} to ${this.queuedVetStaked}"
            )
        }

        if (!this.exitingVetStaked.equals(other.exitingVetStaked)) {
            println(
                "exitingVetStaked changed from ${other.exitingVetStaked} to ${this.exitingVetStaked}"
            )
        }

        if (this.cycleEndBlock != other.cycleEndBlock) {
            println("cycleEndBlock changed from ${other.cycleEndBlock} to ${this.cycleEndBlock}")
        }

        if (!this.totalRewards.equals(other.totalRewards)) {
            println("totalRewards changed from ${other.totalRewards} to ${this.totalRewards}")
        }

        if (!this.blockProbability.equals(other.blockProbability)) {
            println(
                "blockProbability changed from ${other.blockProbability} to ${this.blockProbability}"
            )
        }

        if (!this.blocksPerEpoch.equals(other.blocksPerEpoch)) {
            println("blocksPerEpoch changed from ${other.blocksPerEpoch} to ${this.blocksPerEpoch}")
        }

        if (!this.totalTvl.equals(other.totalTvl)) {
            println("totalTvl changed from ${other.totalTvl} to ${this.totalTvl}")
        }

        if (!this.validatorTvl.equals(other.validatorTvl)) {
            println("validatorTvl changed from ${other.validatorTvl} to ${this.validatorTvl}")
        }

        if (!this.delegatorTvl.equals(other.delegatorTvl)) {
            println("delegatorTvl changed from ${other.delegatorTvl} to ${this.delegatorTvl}")
        }

        if (!this.validatorTvlPercentage.equals(other.validatorTvlPercentage)) {
            println(
                "validatorTvlPercentage changed from ${other.validatorTvlPercentage} to ${this.validatorTvlPercentage}"
            )
        }

        if (!this.tvlBasedYield.equals(other.tvlBasedYield)) {
            println("tvlBasedYield changed from ${other.tvlBasedYield} to ${this.tvlBasedYield}")
        }

        if (!this.validatorYield.equals(other.validatorYield)) {
            println("validatorYield changed from ${other.validatorYield} to ${this.validatorYield}")
        }

        if (!this.avgDelegatorYield.equals(other.avgDelegatorYield)) {
            println(
                "avgDelegatorYield changed from ${other.avgDelegatorYield} to ${this.avgDelegatorYield}"
            )
        }

        if (!this.totalWeight.equals(other.totalWeight)) {
            println("totalWeight changed from ${other.totalWeight} to ${this.totalWeight}")
        }

        if (this.online != other.online) {
            println("online changed from ${other.online} to ${this.online}")
        }

        if (this.completedPeriods != other.completedPeriods) {
            println(
                "completedPeriods changed from ${other.completedPeriods} to ${this.completedPeriods}"
            )
        }

        if (this.startBlock != other.startBlock) {
            println("startBlock changed from ${other.startBlock} to ${this.startBlock}")
        }

        if (this.cyclePeriodLength != other.cyclePeriodLength) {
            println(
                "cyclePeriodLength changed from ${other.cyclePeriodLength} to ${this.cyclePeriodLength}"
            )
        }

        if (!this.blocksPerYear.equals(other.blocksPerYear)) {
            println("blocksPerYear changed from ${other.blocksPerYear} to ${this.blocksPerYear}")
        }

        if (!this.percentageOffline.equals(other.percentageOffline)) {
            println(
                "percentageOffline changed from ${other.percentageOffline} to ${this.percentageOffline}"
            )
        }

        if (this.offlineBlocks != other.offlineBlocks) {
            println("offlineBlocks changed from ${other.offlineBlocks} to ${this.offlineBlocks}")
        }
    }
}

@Document("validators_archives")
@JsonView(Views.Public::class)
data class ValidatorArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: Validator) : Archive<Validator>

fun Decimal128?.equals(other: Decimal128?): Boolean {
    if (this == null && other == null) return true
    if (this == null || other == null) return false
    return this.compareTo(other) == 0
}
