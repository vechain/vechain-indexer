package org.vechain.indexer.b3tr.action.repository

import org.springframework.cache.annotation.Cacheable
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

    fun findByUserAndDate(user: String, date: String): List<AppDailyActionSummary>

    fun findByAppIdAndUserAndDate(appId: String, user: String, date: String): AppDailyActionSummary?

    @Cacheable(value = ["app_daily_action_countByAppIdAndDate"], key = "#appId + '-' + #date")
    fun countByAppIdAndDate(appId: String, date: String): Long

    @Cacheable(
        value = ["app_daily_action_countByTotalRewardAmountGreaterThanAndAppIdAndDate"],
        key = "#totalRewardAmount + '-' + #appId + '-' + #date",
    )
    fun countByTotalRewardAmountGreaterThanAndAppIdAndDate(
        totalRewardAmount: java.math.BigDecimal,
        appId: String,
        date: String,
    ): Long

    @Cacheable(
        value = ["app_daily_action_countByActionsRewardedGreaterThanAndAppIdAndDate"],
        key = "#actionsRewarded + '-' + #appId + '-' + #date",
    )
    fun countByActionsRewardedGreaterThanAndAppIdAndDate(
        actionsRewarded: Long,
        appId: String,
        date: String,
    ): Long
}
