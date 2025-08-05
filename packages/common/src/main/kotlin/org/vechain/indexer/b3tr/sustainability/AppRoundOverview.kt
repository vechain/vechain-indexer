package org.vechain.indexer.b3tr.sustainability

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.shared.AppOverviewDocument

/**
 * Sustainable overview This model is used to track how apps are doing in terms of sustainability
 * over each round.
 */
@Document(collection = "app_round_sustainability_overviews")
data class AppRoundOverview
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val appId: String,
    override val user: String,
    val roundId: Int,
    override val actionsRewarded: Long,
    override val totalRewardAmount: Double,
    override val totalImpact: Impact?,
) : AppOverviewDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        appId: String,
        user: String,
        roundId: Int,
        actionsRewarded: Long,
        totalRewardAmount: Double,
        totalImpact: Impact?,
    ) : this(
        id = generateId(appId, user, roundId),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        appId = appId,
        user = user,
        roundId = roundId,
        actionsRewarded = actionsRewarded,
        totalRewardAmount = totalRewardAmount,
        totalImpact = totalImpact,
    )

    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        fun generateId(appId: String, user: String, roundId: Int): String =
            DigestUtils.sha1Hex("$appId-$user-$roundId")
    }
}

@Document(collection = "app_round_sustainability_overviews_archives")
data class AppRoundOverviewArchive(
    @Id override val id: String,
    override val data: AppRoundOverview,
) : Archive<AppRoundOverview>
