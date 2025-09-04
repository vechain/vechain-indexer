package org.vechain.indexer.b3tr.shared

import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.b3tr.sustainability.Impact

interface UserActionSummaryDocument : VersionedDocument {
    val entity: String
    val actionsRewarded: Long
    val totalRewardAmount: Double
    val totalImpact: Impact?
}
