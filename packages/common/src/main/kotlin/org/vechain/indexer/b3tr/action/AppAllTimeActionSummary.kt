package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.shared.AppActionSummaryDocument
import org.vechain.indexer.utils.IdUtils.generateId

@Document(collection = "b3tr_app_action_summaries_all_time")
data class AppAllTimeActionSummary
@ConstructorBinding
constructor(
    @Id val id: String,
    override val version: Int,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    override val appId: String,
    override val user: String,
    override val actionsRewarded: Long,
    @Field(targetType = FieldType.DECIMAL128) override val totalRewardAmount: BigDecimal,
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
        totalRewardAmount: BigDecimal,
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

    override fun getDocumentId(): String = id
}

@Document(collection = "b3tr_app_action_summaries_all_time_archives")
data class AppAllTimeActionSummaryArchive(
    @Id override val id: String,
    override val data: AppAllTimeActionSummary,
) : Archive<AppAllTimeActionSummary>
