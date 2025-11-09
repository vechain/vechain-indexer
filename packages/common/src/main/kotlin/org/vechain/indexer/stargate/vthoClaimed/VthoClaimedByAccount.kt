package org.vechain.indexer.stargate.vthoClaimed

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive

@Document(collection = "stargate_vtho_claimed_by_account")
data class VthoClaimedByAccount
@ConstructorBinding
constructor(
    override val version: Int,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val total: BigInteger,
    val legacyRewards: BigInteger,
    val delegationRewards: BigInteger,
    val account: String,
    val tokenId: String?,
    /** Could either be the `account` or `account_tokenId` */
    @Id val id: String,
) : VersionedDocument {
    @JsonIgnore
    override fun getDocumentId(): String = if (tokenId != null) "${account}_${tokenId}" else account
}

@Document(collection = "stargate_vtho_claimed_by_account_archives")
data class VthoClaimedByAccountArchive(
    @Id override val id: String,
    override val data: VthoClaimedByAccount,
) : Archive<VthoClaimedByAccount>
