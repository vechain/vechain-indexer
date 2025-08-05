package org.vechain.indexer.b3tr.voting

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.Block

@Document(collection = "proposal_results")
data class ProposalResult
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val proposalId: String,
    val support: Support,
    val voters: Long,
    val totalWeight: BigInteger,
    val totalPower: BigInteger,
) : VersionedDocument {
    constructor(
        version: Int,
        block: Block,
        proposalId: String,
        support: Support,
        voters: Long,
        totalWeight: BigInteger,
        totalPower: BigInteger,
    ) : this(
        version = version,
        id = DigestUtils.sha1Hex("$proposalId-$support"),
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
        proposalId = proposalId,
        support = support,
        voters = voters,
        totalWeight = totalWeight,
        totalPower = totalPower,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "proposal_result_archives")
data class ProposalResultArchive(@Id override val id: String, override val data: ProposalResult) :
    Archive<ProposalResult>
