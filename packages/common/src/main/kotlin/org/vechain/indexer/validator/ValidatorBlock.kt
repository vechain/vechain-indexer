package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidatorBlock(
    @JsonIgnore val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val validator: String,
    val blockReward: BigInteger? = null,
    val priorityReward: BigInteger? = null,
    val total: BigInteger? = null,
    val status: BlockStatus,
    val delegatorRewards: BigInteger? = null,
    val validatorRewards: BigInteger? = null,
    val blocksOffline: Long? = null,
    val onlineBlock: Long? = null,
    @JsonIgnore val isHourly: Boolean? = null,
    @JsonIgnore val isDaily: Boolean? = null,
    @JsonIgnore val isWeekly: Boolean? = null,
    @JsonIgnore val isMonthly: Boolean? = null,
) : IndexedDocument
