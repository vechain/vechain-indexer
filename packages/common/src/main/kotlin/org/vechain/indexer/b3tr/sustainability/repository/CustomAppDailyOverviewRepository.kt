package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.sustainability.AppDailyOverview

interface CustomAppDailyOverviewRepository {
    fun findAppUserOverviewsByFilters(
        appId: String?,
        user: String?,
        startDate: String?,
        endDate: String?,
        pageable: Pageable,
    ): Slice<AppDailyOverview>
}
