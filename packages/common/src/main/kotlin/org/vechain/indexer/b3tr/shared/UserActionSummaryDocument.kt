package org.vechain.indexer.b3tr.shared

import java.math.BigDecimal
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.b3tr.action.Impact

interface UserActionSummaryDocument : VersionedDocument {
    val entity: String
    val actionsRewarded: Long
    val totalRewardAmount: BigDecimal
    val totalImpact: Impact?
}
