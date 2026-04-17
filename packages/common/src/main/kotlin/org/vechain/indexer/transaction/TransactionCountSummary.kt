package org.vechain.indexer.transaction

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.TRANSACTION_COUNT.COLLECTION)
data class TransactionCountSummary
@ConstructorBinding
@JsonCreator
constructor(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore override val version: Int,
    val totalTransactions: BigInteger,
    val totalClauses: BigInteger,
    val totalRevertedTransactions: BigInteger,
    val totalRevertedClauses: BigInteger,
    @JsonIgnore @Id val id: String = SUMMARY_ID,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        const val SUMMARY_ID = "summary"
    }
}
