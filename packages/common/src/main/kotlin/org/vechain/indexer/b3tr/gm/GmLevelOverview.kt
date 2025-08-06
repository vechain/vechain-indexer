package org.vechain.indexer.b3tr.gm

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive

@Document(collection = "gm_level_overviews")
data class GmLevelOverview
@ConstructorBinding
constructor(
    @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val nfts: Long,
    val b3trDonated: BigInteger,
    val nodeHolders: Long,
    val level: Int,
) : VersionedDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        gmLevel: GmLevelName,
        nfts: Long,
        b3trDonated: BigInteger,
        nodeHolders: Long,
    ) : this(
        version = version,
        id = gmLevel.name,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        level = gmLevel.ordinal,
        nfts = nfts,
        b3trDonated = b3trDonated,
        nodeHolders = nodeHolders,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "gm_level_overviews_archives")
data class GmLevelOverviewArchive(@Id override val id: String, override val data: GmLevelOverview) :
    Archive<GmLevelOverview>
