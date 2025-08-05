package org.vechain.indexer.b3tr.gm

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive

@Document(collection = "gm_nfts")
data class GalaxyMemberNFT
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String, // tokenId as string
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val level: GMLevelName,
    val attachedNodeId: String?,
    val b3trDonated: BigInteger,
    val owner: String,
) : VersionedDocument {
    constructor(
        tokenId: String,
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        owner: String,
        level: GMLevelName,
        attachedNodeId: String?,
        b3trDonated: BigInteger,
    ) : this(
        id = tokenId,
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        level = level,
        attachedNodeId = attachedNodeId,
        b3trDonated = b3trDonated,
        owner = owner,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "gm_nfts_archives")
data class GalaxyMemberNFTArchive(@Id override val id: String, override val data: GalaxyMemberNFT) :
    Archive<GalaxyMemberNFT>
