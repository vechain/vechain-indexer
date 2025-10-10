package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
@Repository
interface AppAllTimeActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<AppAllTimeActionSummary, String> {
    fun findAllByAppId(appId: String, pageable: Pageable): Slice<AppAllTimeActionSummary>

    fun findAllByUser(user: String, pageable: Pageable): Slice<AppAllTimeActionSummary>

    fun countByTotalRewardAmountGreaterThanAndAppId(
        totalRewardAmount: BigDecimal,
        appId: String,
    ): Long

    fun countByActionsRewardedGreaterThanAndAppId(actionsRewarded: Long, appId: String): Long

    fun findAppIdsByUser(user: String): List<AppAllTimeActionSummary>

    @Cacheable(value = ["app_all_time_action_countByAppId"], key = "#appId")
    fun countByAppId(appId: String): Long
}
