package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.sustainability.AppOverview

@Profile("b3tr", "sustainability", "sustainability-apps-all")
@Repository
interface AppOverviewRepository : BasePagingAndSortingIndexedRepository<AppOverview, String> {
    fun findAllByAppId(appId: String, pageable: Pageable): Slice<AppOverview>

    fun findAllByUser(user: String, pageable: Pageable): Slice<AppOverview>

    fun countByTotalRewardAmountGreaterThanAndAppId(totalRewardAmount: Double, appId: String): Long

    fun countByActionsRewardedGreaterThanAndAppId(actionsRewarded: Long, appId: String): Long

    fun findAppIdsByUser(user: String): List<AppOverview>

    fun countByAppId(appId: String): Long
}
