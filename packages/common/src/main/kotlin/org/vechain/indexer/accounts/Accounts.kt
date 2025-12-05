package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.Views

@Document(collection = "total_accounts")
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Accounts(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val total: Long? = null,
    val timeFrame: TimeFrame? = null,
    val dayOfMonth: Long? = null, // 25
    val weekOfYear: Long? = null, // 43
    val month: Long? = null, // 10 (October)
    val year: Long? = null, // 2025
    @JsonIgnore val dayTotal: Long? = null,
    @JsonIgnore val weekTotal: Long? = null,
    @JsonIgnore val monthTotal: Long? = null,
    @JsonIgnore val yearTotal: Long? = null,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}

@Document("total_accounts_archives")
@JsonView(Views.Public::class)
data class AccountsArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: Accounts) : Archive<Accounts>
