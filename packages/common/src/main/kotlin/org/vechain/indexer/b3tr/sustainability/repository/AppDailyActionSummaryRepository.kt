package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.sustainability.AppDailyActionSummary

@Profile("b3tr", "b3tr-sustainability", "b3tr-app-daily-action-summary")
@Repository
interface AppDailyActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<AppDailyActionSummary, String>,
    CustomAppDailyActionSummaryRepository {
    fun findAppIdsByUserAndDate(user: String, date: String): List<AppDailyActionSummary>

    fun countByAppIdAndDate(appId: String, date: String): Long
}
