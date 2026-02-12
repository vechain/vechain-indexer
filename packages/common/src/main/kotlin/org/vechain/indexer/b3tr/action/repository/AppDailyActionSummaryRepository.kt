package org.vechain.indexer.b3tr.action.repository

import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.action.AppDailyActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
@Repository
interface AppDailyActionSummaryRepository :
    BaseIndexedRepository<AppDailyActionSummary, String>, CustomAppDailyActionSummaryRepository {

    @Query("{ 'appId': ?0, 'date': ?1 }")
    fun findAllByAppIdAndDate(
        appId: String,
        date: String,
        pageable: Pageable,
    ): Slice<AppDailyActionSummary>

    @Query("{ 'user': ?0, 'date': ?1 }")
    fun findByUserAndDate(user: String, date: String): List<AppDailyActionSummary>

    @Query("{ 'appId': ?0, 'user': ?1, 'date': ?2 }")
    fun findByAppIdAndUserAndDate(appId: String, user: String, date: String): AppDailyActionSummary?

    @Cacheable(value = ["app_daily_action_countByAppIdAndDate"], key = "#appId + '-' + #date")
    @Query(value = "{ 'appId': ?0, 'date': ?1 }", count = true)
    fun countByAppIdAndDate(appId: String, date: String): Long

    @Cacheable(
        value = ["app_daily_action_countByTotalRewardAmountGreaterThanAndAppIdAndDate"],
        key = "#totalRewardAmount.stripTrailingZeros().toPlainString() + '-' + #appId + '-' + #date",
    )
    @Query(value = "{ 'totalRewardAmount': { '\$gt': ?0 }, 'appId': ?1, 'date': ?2 }", count = true)
    fun countByTotalRewardAmountGreaterThanAndAppIdAndDate(
        totalRewardAmount: java.math.BigDecimal,
        appId: String,
        date: String,
    ): Long

    @Cacheable(
        value = ["app_daily_action_countByActionsRewardedGreaterThanAndAppIdAndDate"],
        key = "#actionsRewarded + '-' + #appId + '-' + #date",
    )
    @Query(value = "{ 'actionsRewarded': { '\$gt': ?0 }, 'appId': ?1, 'date': ?2 }", count = true)
    fun countByActionsRewardedGreaterThanAndAppIdAndDate(
        actionsRewarded: Long,
        appId: String,
        date: String,
    ): Long
}
