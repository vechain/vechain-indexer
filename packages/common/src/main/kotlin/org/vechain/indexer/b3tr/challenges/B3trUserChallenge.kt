package org.vechain.indexer.b3tr.challenges

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.utils.IdUtils.generateId

@Document(collection = IndexerNames.B3TR_USER_CHALLENGES.COLLECTION)
data class B3trUserChallenge
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val wallet: String,
    val challengeId: Long,
    val challengeCreatedAtBlockTimestamp: Long,
) : VersionedDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        wallet: String,
        challengeId: Long,
        challengeCreatedAtBlockTimestamp: Long,
    ) : this(
        id = documentId(wallet, challengeId),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        wallet = wallet,
        challengeId = challengeId,
        challengeCreatedAtBlockTimestamp = challengeCreatedAtBlockTimestamp,
    )

    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        fun documentId(wallet: String, challengeId: Long): String =
            generateId(wallet, "$challengeId")
    }
}
