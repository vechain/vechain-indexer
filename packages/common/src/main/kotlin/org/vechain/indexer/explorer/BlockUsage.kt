package org.vechain.indexer.explorer

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.accounts.TimeFrame

data class BlockUsage
@ConstructorBinding
@JsonCreator
constructor(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val cumulativeGasLimit: BigInteger,
    val cumulativeGasUsed: BigInteger,
    val cumulativeBaseFeePerGas: BigInteger?,
    val cumulativeNumTransactions: BigInteger,
    val cumulativeNumClauses: BigInteger,
    /** The boundaries this block is the first past, which the API samples a range by. */
    @JsonIgnore val timeFrames: List<TimeFrame> = emptyList(),
) : IndexedDocument
