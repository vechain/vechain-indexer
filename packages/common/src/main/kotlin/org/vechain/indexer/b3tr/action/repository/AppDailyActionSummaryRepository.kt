package org.vechain.indexer.b3tr.action.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.action.AppDailyActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
@Repository
interface AppDailyActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<AppDailyActionSummary, String>,
    CustomAppDailyActionSummaryRepository {

    fun findAllByAppIdAndDate(
        appId: String,
        date: String,
        pageable: Pageable,
    ): Slice<AppDailyActionSummary>

    fun findAppIdsByUserAndDate(user: String, date: String): List<AppDailyActionSummary>

    fun countByAppIdAndDate(appId: String, date: String): Long
}
