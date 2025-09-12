package org.vechain.indexer.b3tr.shared

import java.math.BigDecimal
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.b3tr.action.Impact

interface AppActionSummaryDocument : VersionedDocument {
    val appId: String
    val user: String
    val actionsRewarded: Long
    val totalRewardAmount: BigDecimal
    val totalImpact: Impact?
}
