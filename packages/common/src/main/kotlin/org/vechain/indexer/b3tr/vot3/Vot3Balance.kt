package org.vechain.indexer.b3tr.vot3

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

@Document(collection = IndexerNames.VOT3_BALANCE.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Vot3Balance(
    @Id val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    var balance: BigInteger,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}

@Document(collection = "vot3_balance_archives")
data class Vot3BalanceArchive(@Id override val id: String, override val data: Vot3Balance) :
    Archive<Vot3Balance>
