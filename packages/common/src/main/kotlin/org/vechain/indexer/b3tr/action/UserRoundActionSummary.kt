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
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.shared.UserActionSummaryDocument

/**
 * Sustainable overview This model is used to track how apps and users are doing in terms of
 * sustainability over each round.
 */
@Document(collection = "b3tr_user_action_summaries_round")
data class UserRoundActionSummary
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
        roundId: Int,
        actionsRewarded: Long,
        totalRewardAmount: BigDecimal,
        totalImpact: Impact?,
    ) : this(
        id =
            if (entityType == EntityType.GLOBAL) generateId("GLOBAL", "$roundId")
            else generateId(entity, "$roundId"),
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

@Document(collection = "b3tr_user_action_summaries_round_archives")
data class UserRoundActionSummaryArchive(
    @Id override val id: String,
    override val data: UserRoundActionSummary,
) : Archive<UserRoundActionSummary>
