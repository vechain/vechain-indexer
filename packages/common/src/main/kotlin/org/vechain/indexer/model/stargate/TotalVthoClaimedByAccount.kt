package org.vechain.indexer.model.stargate

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument

@Document(collection = "stargate_total_vtho_claimed_by_account")
data class TotalVthoClaimedByAccount
@ConstructorBinding
constructor(
    override val version: Int,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val value: BigInteger,
    @Id val account: String,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = account
}

@Document(collection = "stargate_total_vtho_claimed_by_account_archives")
data class TotalVthoClaimedByAccountArchive(
    @Id override val id: String,
    override val data: TotalVthoClaimedByAccount,
) : Archive<TotalVthoClaimedByAccount>
