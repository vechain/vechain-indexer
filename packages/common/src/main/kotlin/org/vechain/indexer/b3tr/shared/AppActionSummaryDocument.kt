package org.vechain.indexer.b3tr.shared

import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.b3tr.action.Impact

interface AppActionSummaryDocument : VersionedDocument {
    val appId: String
    val user: String
    val actionsRewarded: Long
    val totalRewardAmount: Double
    val totalImpact: Impact?
}
