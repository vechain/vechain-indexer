package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountOverview(
    @JsonIgnore val address: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val firstSeen: Long,
    var lastSeen: Long,
    var transactionsSent: Long,
    var clausesSent: Long,
    var vthoBurned: BigInteger,
    var vthoDelegated: BigInteger,
    var gasUsed: BigInteger,
    var vetSent: BigInteger,
    var vetReceived: BigInteger,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}
