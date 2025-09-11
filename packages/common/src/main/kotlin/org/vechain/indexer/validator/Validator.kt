package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.stargate.TokenLevel

@Document(collection = "validators")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Validator(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val endorser: String? = null, // address of the endorser
    val status: Status? = null, // active, inactive, jailed, etc.
    val vetStaked: String? = null, // amount of VET staked
    val delegations: Map<TokenLevel, Long> = emptyMap(), // number of delegations by level
    @JsonIgnore
    val delegationIds: Map<String, TokenLevel> = emptyMap(), // mapping of delegation ID to level
    val cycleEndblock: Long? = null, // end block of the current cycle
    val totalRewards: String? = null, // total rewards earned
    val blockProbability: String? = null,
    val blocksPerEpoch: String? = null,
    val totalTvl: String? = null,
    val validatorTvl: String? = null,
    val delegatorTvl: String? = null,
    val validatorTvlPercentage: String? = null,
    val tvlBasedYield: String? = null,
    val validatorYield: String? = null,
    val avgDelegatorYield: String? = null,
    val totalWeight: String? = null,
    val online: Boolean? = null,
    val completedPeriods: Long? = null,
    val startBlock: Long? = null,
    val stakingPeriodLength: Long? = null,
    val blocksPerYear: Long? = null,
    val hasDelegations: Boolean? = null,
    val offlineBlocks: Long? = null,
    @JsonIgnore val totalVTHOSupply: BigDecimal,
) : IndexedDocument
