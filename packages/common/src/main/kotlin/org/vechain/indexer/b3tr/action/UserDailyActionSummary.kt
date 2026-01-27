package org.vechain.indexer.b3tr.action

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.shared.UserActionSummaryDocument
import org.vechain.indexer.utils.IdUtils.generateId

/**
 * Sustainable overview This model is used to track how apps and users are doing in terms of
 * sustainability on a daily basis.
 */
data class UserDailyActionSummary(
    @JsonIgnore val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val entity: String,
    @JsonIgnore val entityType: EntityType,
    val date: String,
    override val actionsRewarded: Long,
    override val totalRewardAmount: BigDecimal,
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
