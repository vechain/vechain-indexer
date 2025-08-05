package org.vechain.indexer.b3tr.sustainability

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.shared.OverviewDocument

/**
 * Sustainable overview This model is used to track how apps and users are doing in terms of
 * sustainability on a daily basis.
 */
@Document(collection = "sustainability_overviews_daily")
data class DailyOverview
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
    override val totalRewardAmount: Double,
    override val totalImpact: Impact?,
) : OverviewDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        entity: String,
        entityType: EntityType,
        date: String,
        actionsRewarded: Long,
        totalRewardAmount: Double,
        totalImpact: Impact?,
    ) : this(
        id =
            if (entityType == EntityType.GLOBAL) generateGlobalId(date)
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

@Document(collection = "sustainability_overviews_daily_archives")
data class DailyOverviewArchive(@Id override val id: String, override val data: DailyOverview) :
    Archive<DailyOverview>

fun generateId(entity: String, date: String): String {
    return DigestUtils.sha1Hex("${entity}-${date}")
}

fun generateGlobalId(date: String): String {
    return generateId("GLOBAL", date)
}
