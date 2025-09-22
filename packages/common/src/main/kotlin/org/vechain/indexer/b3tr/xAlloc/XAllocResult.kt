package org.vechain.indexer.b3tr.xAlloc

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.action.IdUtils.generateId

@Document(collection = "b3tr_x_alloc_results")
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
    val totalVotes: BigInteger,
) : VersionedDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        roundId: Int,
        appId: String,
        voters: Long,
        totalVotes: BigInteger,
    ) : this(
        version = version,
        id = generateId("$roundId", appId),
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        roundId = roundId,
        appId = appId,
        voters = voters,
        totalVotes = totalVotes,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "b3tr_x_alloc_result_archives")
data class XAllocResultArchive(@Id override val id: String, override val data: XAllocResult) :
    Archive<XAllocResult>
