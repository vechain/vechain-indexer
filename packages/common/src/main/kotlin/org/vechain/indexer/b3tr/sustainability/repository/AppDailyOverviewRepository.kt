package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.sustainability.AppDailyOverview

@Profile("b3tr", "sustainability", "sustainability-apps-daily")
@Repository
interface AppDailyOverviewRepository :
    BasePagingAndSortingIndexedRepository<AppDailyOverview, String>,
    CustomAppDailyOverviewRepository {
    fun findAppIdsByUserAndDate(user: String, date: String): List<AppDailyOverview>

    fun countByAppIdAndDate(appId: String, date: String): Long
}
