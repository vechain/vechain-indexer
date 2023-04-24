package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.model.rest.ExpandedBlockResponse


@Document(collection = "blocks")
data class Block @ConstructorBinding @JsonCreator constructor(
    @Id
    override val blockId: String,
    @Indexed(unique = true, direction = IndexDirection.DESCENDING)
    @JsonProperty("number")
    override val blockNumber: Long,
    val size: Long,
    val parentID: String,
    val timestamp: Long,
    val gasLimit: Long,
    val beneficiary: String,
    val gasUsed: Long,
    val totalScore: Long,
    val txsRoot: String,
    val txsFeatures: Int,
    val stateRoot: String,
    val receiptsRoot: String,
    val com: Boolean,
    @Indexed
    val signer: String,
    val isTrunk: Boolean,
    val isFinalized: Boolean,
    val transactions: List<TransactionData>
) : IndexedDocument {
    constructor(blockResponse: ExpandedBlockResponse) : this(
        blockId = blockResponse.id,
        blockNumber = blockResponse.number,
        size = blockResponse.size,
        parentID = blockResponse.parentID,
        timestamp = blockResponse.timestamp,
        gasLimit = blockResponse.gasLimit,
        beneficiary = blockResponse.beneficiary,
        gasUsed = blockResponse.gasUsed,
        totalScore = blockResponse.totalScore,
        txsRoot = blockResponse.txsRoot,
        txsFeatures = blockResponse.txsFeatures,
        stateRoot = blockResponse.stateRoot,
        receiptsRoot = blockResponse.receiptsRoot,
        com = blockResponse.com,
        signer = blockResponse.signer,
        isTrunk = blockResponse.isTrunk,
        isFinalized = blockResponse.isFinalized,
        transactions = blockResponse.transactions.map {
            TransactionData(
                blockNumber = blockResponse.number,
                blockId = blockResponse.id,
                tx = it
            )
        }
    )
}