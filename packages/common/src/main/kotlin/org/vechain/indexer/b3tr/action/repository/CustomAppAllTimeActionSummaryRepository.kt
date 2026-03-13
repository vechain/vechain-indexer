package org.vechain.indexer.b3tr.action.repository

interface CustomAppAllTimeActionSummaryRepository {
    /**
     * Counts documents for each appId in a single aggregation query. Returns a map from appId to
     * count.
     */
    fun countByAppIds(appIds: Set<String>): Map<String, Long>
}
