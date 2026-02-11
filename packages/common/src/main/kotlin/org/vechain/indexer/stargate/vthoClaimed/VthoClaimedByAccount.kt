package org.vechain.indexer.stargate.vthoClaimed

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.utils.IdUtils

@Document(collection = IndexerNames.VTHO_CLAIMED_BY_ACCOUNT.COLLECTION)
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
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: BigInteger,
        legacyRewards: BigInteger,
        delegationRewards: BigInteger,
        account: String,
        tokenId: String?,
    ) : this(
        id = tokenId?.let { IdUtils.generateId(account, tokenId) } ?: IdUtils.generateId(account),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        total = total,
        legacyRewards = legacyRewards,
        delegationRewards = delegationRewards,
        account = account,
        tokenId = tokenId,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}
