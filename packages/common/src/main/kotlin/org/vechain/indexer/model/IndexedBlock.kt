package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.thor.model.Block
import org.vechain.thor.model.Transaction

@Document(collection = "blocks")
data class IndexedBlock
@ConstructorBinding
@JsonCreator
constructor(
  @Id @JsonProperty("id") override val blockId: String,
  @JsonProperty("number") override val blockNumber: Long,
  @JsonProperty("timestamp") override val blockTimestamp: Long,
  val size: Long,
  val parentID: String,
  val gasLimit: Long,
  val beneficiary: String,
  val gasUsed: Long,
  val totalScore: Long,
  val txsRoot: String,
  val txsFeatures: Int,
  val stateRoot: String,
  val receiptsRoot: String,
  val com: Boolean,
  val signer: String,
  val isTrunk: Boolean,
  var isFinalized: Boolean,
  val transactions: List<Transaction>
) : IndexedDocument {
    constructor(
      block: Block
    ) : this(
      blockId = block.id,
      blockNumber = block.number,
      size = block.size,
      parentID = block.parentID,
      blockTimestamp = block.timestamp,
      gasLimit = block.gasLimit,
      beneficiary = block.beneficiary,
      gasUsed = block.gasUsed,
      totalScore = block.totalScore,
      txsRoot = block.txsRoot,
      txsFeatures = block.txsFeatures,
      stateRoot = block.stateRoot,
      receiptsRoot = block.receiptsRoot,
      com = block.com,
      signer = block.signer,
      isTrunk = block.isTrunk,
      isFinalized = block.isFinalized,
      transactions = block.transactions
    )
}
