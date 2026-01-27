package org.vechain.indexer.b3tr.action

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.vechain.indexer.b3tr.shared.AppActionSummaryDocument
import org.vechain.indexer.utils.IdUtils.generateId

/**
 * Sustainable overview This model is used to track how apps are doing in terms of sustainability
 * over each round.
 */
data class AppRoundActionSummary(
    @JsonIgnore val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val appId: String,
    override val user: String,
    val roundId: Int,
    override val actionsRewarded: Long,
    override val totalRewardAmount: BigDecimal,
    override val totalImpact: Impact?,
) : AppActionSummaryDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        appId: String,
        user: String,
        roundId: Int,
        actionsRewarded: Long,
        totalRewardAmount: BigDecimal,
        totalImpact: Impact?,
    ) : this(
        id = generateId(appId, user, "$roundId"),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        appId = appId,
        user = user,
        roundId = roundId,
        actionsRewarded = actionsRewarded,
        totalRewardAmount = totalRewardAmount,
        totalImpact = totalImpact,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}
