package org.vechain.indexer.b3tr.action

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.shared.UserActionSummaryDocument
import org.vechain.indexer.utils.IdUtils.generateId

/**
 * Sustainable overview This model is used to track how apps and users are doing in terms of
 * sustainability on a daily basis.
 */
@Document(collection = IndexerNames.USER_DAILY_ACTION_SUMMARY.COLLECTION)
data class UserDailyActionSummary
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val entity: String,
    @JsonIgnore val entityType: EntityType,
    val date: String,
    override val actionsRewarded: Long,
    @Field(targetType = FieldType.DECIMAL128) override val totalRewardAmount: BigDecimal,
    override val totalImpact: Impact?,
) : UserActionSummaryDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        entity: String,
        entityType: EntityType,
        date: String,
        actionsRewarded: Long,
        totalRewardAmount: BigDecimal,
        totalImpact: Impact?,
    ) : this(
        id =
            if (entityType == EntityType.GLOBAL) generateId(EntityType.GLOBAL.name, date)
            else generateId(entity, date),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        entity = entity,
        entityType = entityType,
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

@Document(collection = "b3tr_user_action_summaries_daily_archives")
data class UserDailyActionSummaryArchive(
    @Id override val id: String,
    override val data: UserDailyActionSummary,
) : Archive<UserDailyActionSummary>
