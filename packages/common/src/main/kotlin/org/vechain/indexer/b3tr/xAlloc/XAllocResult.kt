package org.vechain.indexer.b3tr.xAlloc

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

data class XAllocResult(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val roundId: Int,
    val appId: String,
    val voters: Long,
    val votesReceived: BigInteger,
    val totalAmount: BigDecimal? = null,
    val unallocatedAmount: BigDecimal? = null,
    val teamAllocationAmount: BigDecimal? = null,
    val rewardsAllocationAmount: BigDecimal? = null,
) : IndexedDocument
