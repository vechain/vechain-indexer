package org.vechain.indexer.b3tr.sustainability

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.shared.OverviewDocument

@Document(collection = "all_time_sustainability_overviews")
data class Overview
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val entity: String,
    @JsonIgnore val entityType: EntityType,
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
        actionsRewarded: Long,
        totalRewardAmount: Double,
        totalImpact: Impact?,
    ) : this(
        id = if (entityType == EntityType.GLOBAL) generateGlobalId() else generateId(entity),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        entity = entity,
        entityType = entityType,
        actionsRewarded = actionsRewarded,
        totalRewardAmount = totalRewardAmount,
        totalImpact = totalImpact,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "all_time_sustainability_overviews_archives")
data class OverviewArchive(@Id override val id: String, override val data: Overview) :
    Archive<Overview>

fun generateId(entity: String): String = DigestUtils.sha1Hex(entity)

fun generateGlobalId(): String = generateId("GLOBAL")
