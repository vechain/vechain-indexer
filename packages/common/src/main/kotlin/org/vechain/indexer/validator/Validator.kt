package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import kotlin.collections.List
import org.bson.types.Decimal128
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.stargate.TokenLevel
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
    val delegations: Map<TokenLevel, Long> = emptyMap(), // number of delegations by level
    @JsonIgnore val delegationInfo: Map<String, Pair<TokenLevel, Status>> = emptyMap(),
    @JsonIgnore val delegationsToBeActioned: List<String> = emptyList(),
    val cycleEndBlock: Long, // end block of the current cycle
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
    val cyclePeriodLength: Long,
    val blocksPerYear: Decimal128? = null,
    val percentageOffline: Decimal128? = null,
    val offlineBlocks: Long? = null,
    @JsonIgnore val totalVTHOSupply: Decimal128,
    @JsonIgnore override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}

@Document("validators_archives")
@JsonView(Views.Public::class)
data class ValidatorArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: Validator) : Archive<Validator>
