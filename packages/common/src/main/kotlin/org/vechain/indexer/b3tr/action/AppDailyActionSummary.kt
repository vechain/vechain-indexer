package org.vechain.indexer.b3tr.action

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.shared.AppActionSummaryDocument

/**
 * Sustainable overview This model is used to track how apps are doing in terms of sustainability on
 * a daily basis.
 */
@Document(collection = "b3tr_app_action_summaries_daily")
data class AppDailyActionSummary
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
    @Field(targetType = FieldType.DECIMAL128) override val totalRewardAmount: BigDecimal,
    override val totalImpact: Impact?,
) : AppActionSummaryDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        appId: String,
        user: String,
        date: String,
        actionsRewarded: Long,
        totalRewardAmount: BigDecimal,
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
}

@Document(collection = "b3tr_app_action_summaries_daily_archives")
data class AppDailyActionSummaryArchive(
    @Id override val id: String,
    override val data: AppDailyActionSummary,
) : Archive<AppDailyActionSummary>
