package org.vechain.indexer.b3tr.action.repository

import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.action.AppRoundActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
@Repository
interface AppRoundActionSummaryRepository : BaseIndexedRepository<AppRoundActionSummary, String> {
    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun findFirstByOrderByBlockNumberDesc(): AppRoundActionSummary?

    @Query("{ 'appId': ?0, 'roundId': ?1 }")
    fun findAllByAppIdAndRoundId(
        appId: String,
        roundId: Int,
        pageable: Pageable,
    ): Slice<AppRoundActionSummary>

    @Query("{ 'user': ?0, 'roundId': ?1 }")
    fun findAppIdsByUserAndRoundId(user: String, roundId: Int): List<AppRoundActionSummary>

    @Query("{ 'appId': ?0, 'user': ?1, 'roundId': ?2 }")
    fun findByAppIdAndUserAndRoundId(
        appId: String,
        user: String,
        roundId: Int,
    ): AppRoundActionSummary?

    @Cacheable(value = ["app_round_countByAppIdAndRoundId"], key = "#appId + '-' + #roundId")
    @Query(value = "{ 'appId': ?0, 'roundId': ?1 }", count = true)
    fun countByAppIdAndRoundId(appId: String, roundId: Int): Long

    @Cacheable(
        value = ["app_round_countByTotalRewardAmountGreaterThanAndAppIdAndRoundId"],
        key =
            "#totalRewardAmount.stripTrailingZeros().toPlainString() + '-' + #appId + '-' + #roundId",
    )
    @Query(
        value = "{ 'totalRewardAmount': { '\$gt': ?0 }, 'appId': ?1, 'roundId': ?2 }",
        count = true,
    )
    fun countByTotalRewardAmountGreaterThanAndAppIdAndRoundId(
        totalRewardAmount: java.math.BigDecimal,
        appId: String,
        roundId: Int,
    ): Long

    @Cacheable(
        value = ["app_round_countByActionsRewardedGreaterThanAndAppIdAndRoundId"],
        key = "#actionsRewarded + '-' + #appId + '-' + #roundId",
    )
    @Query(
        value = "{ 'actionsRewarded': { '\$gt': ?0 }, 'appId': ?1, 'roundId': ?2 }",
        count = true,
    )
    fun countByActionsRewardedGreaterThanAndAppIdAndRoundId(
        actionsRewarded: Long,
        appId: String,
        roundId: Int,
    ): Long
}
