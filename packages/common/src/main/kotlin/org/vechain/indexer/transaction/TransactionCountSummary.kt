package org.vechain.indexer.transaction

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.TRANSACTION_COUNT.COLLECTION)
data class TransactionCountSummary
@ConstructorBinding
@JsonCreator
constructor(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val totalTransactions: BigInteger,
    val totalClauses: BigInteger,
    @JsonIgnore @Id val id: String = blockNumber.toString(),
) : IndexedDocument
