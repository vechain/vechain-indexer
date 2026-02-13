package org.vechain.indexer.b3tr.xAlloc

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.utils.IdUtils.generateId

@Document(collection = IndexerNames.X_ALLOC_RESULT.COLLECTION)
data class XAllocResult
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val roundId: Int,
    val appId: String,
    val voters: Long,
    val votesReceived: BigInteger,
    @Field(targetType = FieldType.DECIMAL128) val totalAmount: BigDecimal?,
    @Field(targetType = FieldType.DECIMAL128) val unallocatedAmount: BigDecimal?,
    @Field(targetType = FieldType.DECIMAL128) val teamAllocationAmount: BigDecimal?,
    @Field(targetType = FieldType.DECIMAL128) val rewardsAllocationAmount: BigDecimal?,
) : VersionedDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        roundId: Int,
        appId: String,
        voters: Long,
        votesReceived: BigInteger,
        totalAmount: BigDecimal? = null,
        unallocatedAmount: BigDecimal? = null,
        teamAllocationAmount: BigDecimal? = null,
        rewardsAllocationAmount: BigDecimal? = null,
    ) : this(
        version = version,
        id = generateId("$roundId", appId),
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        roundId = roundId,
        appId = appId,
        voters = voters,
        votesReceived = votesReceived,
        totalAmount = totalAmount,
        unallocatedAmount = unallocatedAmount,
        teamAllocationAmount = teamAllocationAmount,
        rewardsAllocationAmount = rewardsAllocationAmount,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}
