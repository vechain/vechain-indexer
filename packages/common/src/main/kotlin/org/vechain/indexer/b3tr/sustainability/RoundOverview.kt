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
 * sustainability over each round.
 */
@Document(collection = "round_sustainability_overviews")
data class RoundOverview
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val entity: String,
    @JsonIgnore val entityType: EntityType,
    val roundId: Int,
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
        roundId: Int,
        actionsRewarded: Long,
        totalRewardAmount: Double,
        totalImpact: Impact?,
    ) : this(
        id =
            if (entityType == EntityType.GLOBAL) generateGlobalId(roundId)
            else generateId(entity, roundId),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        entity = entity,
        entityType = entityType,
        roundId = roundId,
        actionsRewarded = actionsRewarded,
        totalRewardAmount = totalRewardAmount,
        totalImpact = totalImpact,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "round_sustainability_overviews_archives")
data class RoundOverviewArchive(@Id override val id: String, override val data: RoundOverview) :
    Archive<RoundOverview>

fun generateId(entity: String, roundId: Int): String = DigestUtils.sha1Hex("$entity-$roundId")

fun generateGlobalId(roundId: Int): String = generateId("GLOBAL", roundId)
