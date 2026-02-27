package org.vechain.indexer.b3tr.balance

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.Views

@Document(collection = IndexerNames.B3TR_BALANCE.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class B3trBalance(
    @Id val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    var balance: BigInteger,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}

@Document(collection = "b3tr_balance_archives")
data class B3trBalanceArchive(@Id override val id: String, override val data: B3trBalance) :
    Archive<B3trBalance>
