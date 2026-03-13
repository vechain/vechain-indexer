package org.vechain.indexer.b3tr.action.repository

interface CustomAppRoundActionSummaryRepository {
    /**
     * Counts documents for each (appId, roundId) pair in a single aggregation query. Returns a map
     * from (appId, roundId) to count.
     */
    fun countByAppIdAndRoundIdPairs(pairs: Set<Pair<String, Int>>): Map<Pair<String, Int>, Long>
}
