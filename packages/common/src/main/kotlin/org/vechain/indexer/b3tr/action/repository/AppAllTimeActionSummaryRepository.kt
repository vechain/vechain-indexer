package org.vechain.indexer.b3tr.action.repository

import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
@Repository
interface AppAllTimeActionSummaryRepository :
    BaseIndexedRepository<AppAllTimeActionSummary, String> {
    @Query("{ 'appId': ?0 }")
    fun findAllByAppId(appId: String, pageable: Pageable): Slice<AppAllTimeActionSummary>

    @Query("{ 'user': ?0 }") fun findAppIdsByUser(user: String): List<AppAllTimeActionSummary>

    @Query("{ 'appId': ?0, 'user': ?1 }")
    fun findByAppIdAndUser(appId: String, user: String): AppAllTimeActionSummary?

    @Cacheable(value = ["app_all_time_action_countByAppId"], key = "#appId")
    @Query(value = "{ 'appId': ?0 }", count = true)
    fun countByAppId(appId: String): Long

    @Cacheable(
        value = ["app_all_time_action_countByTotalRewardAmountGreaterThanAndAppId"],
        key = "#totalRewardAmount.stripTrailingZeros().toPlainString() + '-' + #appId",
    )
    @Query(value = "{ 'totalRewardAmount': { '\$gt': ?0 }, 'appId': ?1 }", count = true)
    fun countByTotalRewardAmountGreaterThanAndAppId(
        totalRewardAmount: java.math.BigDecimal,
        appId: String,
    ): Long

    @Cacheable(
        value = ["app_all_time_action_countByActionsRewardedGreaterThanAndAppId"],
        key = "#actionsRewarded + '-' + #appId",
    )
    @Query(value = "{ 'actionsRewarded': { '\$gt': ?0 }, 'appId': ?1 }", count = true)
    fun countByActionsRewardedGreaterThanAndAppId(actionsRewarded: Long, appId: String): Long
}
