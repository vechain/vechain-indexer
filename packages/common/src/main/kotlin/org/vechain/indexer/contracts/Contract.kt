package org.vechain.indexer.contracts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.Views

@Document(collection = "contracts")
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Contract(
    @JsonIgnore @Id val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    val createdOn: Long,
    val deployer: String,
    val master: String,
    val isErc20: Boolean,
    val isErc721: Boolean,
    val isErc1155: Boolean,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}

@Document("contract_archives")
@JsonView(Views.Public::class)
data class ContractArchive(@Id override val id: String, override val data: Contract) :
    Archive<Contract>
