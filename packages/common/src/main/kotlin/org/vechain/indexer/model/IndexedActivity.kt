package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonView
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.*

enum class ActivityType {
    VET_TRANSFER,
    FUNGIBLE_TRANSFER,
    NON_FUNGIBLE_TRANSFER,
    TRANSACTION,
    DELEGATED_TRANSACTION,
}

@Document(collection = "activities")
@JsonView(Views.Public::class)
data class IndexedActivity
@ConstructorBinding
constructor(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val account: String,
    val type: ActivityType,
    val transaction: Transaction,
) : IndexedDocument {
    constructor(
        tx: Transaction,
        block: Block,
        account: String,
        type: ActivityType,
        ids: Array<String>,
    ) : this(
        DigestUtils.sha1Hex(ids.joinToString("-")),
        block.id,
        block.number,
        block.timestamp,
        account,
        type,
        tx,
    )
}
