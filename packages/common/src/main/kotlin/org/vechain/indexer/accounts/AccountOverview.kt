package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@Document(collection = IndexerNames.ACCOUNT_OVERVIEW.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountOverview(
    @Id val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    val firstSeen: Long,
    var lastSeen: Long,
    var transactionsSent: Long = 0L,
    var clausesSent: Long = 0L,
    var vthoBurned: BigInteger = BigInteger.ZERO,
    var vthoDelegated: BigInteger = BigInteger.ZERO,
    var gasUsed: BigInteger = BigInteger.ZERO,
    var vetSent: BigInteger = BigInteger.ZERO,
    var vetReceived: BigInteger = BigInteger.ZERO,
    var vetBalance: BigInteger = BigInteger.ZERO,
    var vthoBlockRewards: BigInteger = BigInteger.ZERO,
    var vthoPassiveGeneration: BigInteger = BigInteger.ZERO,
    /** Timestamp when passive VTHO was last settled (for calculating time-based generation) */
    @JsonIgnore var lastVthoSettlement: Long? = null,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}
