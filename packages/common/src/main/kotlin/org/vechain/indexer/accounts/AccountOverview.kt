package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.thor.model.Views

@Document(collection = "account_overviews")
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountOverview(
    @JsonIgnore @Id val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    val firstSeen: BlockIdentifier,
    val lastSeen: BlockIdentifier,
    val transactionsSent: Long,
    val clausesSent: Long,
    val vthoGenerated: BigInteger, // Not sure how to calculate this yet
    val vthoBurned: BigInteger,
    val vthoDelegated: BigInteger,
    val gasUsed: BigInteger,
    val vetSent: BigInteger,
    val vetReceived: BigInteger,
    val myContracts: List<MyContract>? = null,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}

@Document(collection = "account_overview_archives")
data class AccountOverviewArchive(@Id override val id: String, override val data: AccountOverview) :
    Archive<AccountOverview>

data class MyContract(
    val contractAddress: String,
    val isDeployer: Boolean,
    val isMasterContract: Boolean,
)
