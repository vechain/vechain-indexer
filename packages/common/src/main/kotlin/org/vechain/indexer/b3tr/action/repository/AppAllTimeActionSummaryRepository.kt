package org.vechain.indexer.b3tr.action.repository

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

    fun findAppIdsByUser(user: String): List<AppAllTimeActionSummary>

    fun findByAppIdAndUser(appId: String, user: String): AppAllTimeActionSummary?

    @Cacheable(value = ["app_all_time_action_countByAppId"], key = "#appId")
    fun countByAppId(appId: String): Long

    @Cacheable(
        value = ["app_all_time_action_countByTotalRewardAmountGreaterThanAndAppId"],
        key = "#totalRewardAmount + '-' + #appId",
    )
    fun countByTotalRewardAmountGreaterThanAndAppId(
        totalRewardAmount: java.math.BigDecimal,
        appId: String,
    ): Long

    @Cacheable(
        value = ["app_all_time_action_countByActionsRewardedGreaterThanAndAppId"],
        key = "#actionsRewarded + '-' + #appId",
    )
    fun countByActionsRewardedGreaterThanAndAppId(actionsRewarded: Long, appId: String): Long
}
