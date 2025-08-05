package org.vechain.indexer.b3tr.sustainability

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.shared.AppOverviewDocument

/**
 * Sustainable overview This model is used to track how apps are doing in terms of sustainability on
 * a daily basis.
 */
@Document(collection = "sustainability_overviews_daily_app")
data class AppDailyOverview
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val appId: String,
    override val user: String,
    val date: String,
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
        date: String,
        actionsRewarded: Long,
        totalRewardAmount: Double,
        totalImpact: Impact?,
    ) : this(
        id = generateId(appId, user, date),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        appId = appId,
        user = user,
        date = date,
        actionsRewarded = actionsRewarded,
        totalRewardAmount = totalRewardAmount,
        totalImpact = totalImpact,
    )

    @JsonIgnore
    override fun getDocumentId(): String {
        return id
    }

    companion object {
        fun generateId(appId: String, user: String, date: String): String =
            DigestUtils.sha1Hex("$appId-$user-$date")
    }
}

@Document(collection = "sustainability_overviews_daily_app_archives")
data class AppDailyOverviewArchive(
    @Id override val id: String,
    override val data: AppDailyOverview,
) : Archive<AppDailyOverview>
