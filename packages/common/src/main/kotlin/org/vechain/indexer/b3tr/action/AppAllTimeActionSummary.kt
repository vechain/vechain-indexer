package org.vechain.indexer.b3tr.action

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.shared.AppActionSummaryDocument

@Document(collection = "b3tr_app_action_summaries_all_time")
data class AppAllTimeActionSummary
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val appId: String,
    override val user: String,
    override val actionsRewarded: Long,
    override val totalRewardAmount: Double,
    override val totalImpact: Impact?,
) : AppActionSummaryDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        user: String,
        appId: String,
        actionsRewarded: Long,
        totalRewardAmount: Double,
        totalImpact: Impact?,
    ) : this(
        id = generateId(appId, user),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        user = user,
        appId = appId,
        actionsRewarded = actionsRewarded,
        totalRewardAmount = totalRewardAmount,
        totalImpact = totalImpact,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "b3tr_app_action_summaries_all_time_archives")
data class AppAllTimeActionSummaryArchive(
    @Id override val id: String,
    override val data: AppAllTimeActionSummary,
) : Archive<AppAllTimeActionSummary>
