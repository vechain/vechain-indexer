package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import org.vechain.indexer.b3tr.shared.AppActionSummaryDocument
import org.vechain.indexer.utils.IdUtils.generateId

data class AppAllTimeActionSummary(
    val id: String,
    override val version: Int,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    override val appId: String,
    override val user: String,
    override val actionsRewarded: Long,
    override val totalRewardAmount: BigDecimal,
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
