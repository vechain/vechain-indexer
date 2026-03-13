package org.vechain.indexer.b3tr.action.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.action.AppDailyActionSummary

interface CustomAppDailyActionSummaryRepository {
    fun findAppUserOverviewsByFilters(
        appId: String?,
        user: String?,
        startDate: String?,
        endDate: String?,
        pageable: Pageable,
    ): Slice<AppDailyActionSummary>

    /**
     * Counts documents for each (appId, date) pair in a single aggregation query. Returns a map
     * from (appId, date) to count.
     */
    fun countByAppIdAndDatePairs(pairs: Set<Pair<String, String>>): Map<Pair<String, String>, Long>
}
