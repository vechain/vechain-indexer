package org.vechain.indexer.explorer

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.BLOCK_USAGE.COLLECTION)
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
    @JsonIgnore val isHourly: Boolean?,
    @JsonIgnore val isDaily: Boolean?,
    @JsonIgnore val isWeekly: Boolean?,
    @JsonIgnore val isMonthly: Boolean?,
    @JsonIgnore @Id val id: String = blockNumber.toString(),
) : IndexedDocument
