package org.vechain.indexer.b3tr.action.repository

import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.action.AppRoundActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
@Repository
interface AppRoundActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<AppRoundActionSummary, String> {
    fun findFirstByOrderByBlockNumberDesc(): AppRoundActionSummary?

    fun findAllByAppIdAndRoundId(
        appId: String,
        roundId: Int,
        pageable: Pageable,
    ): Slice<AppRoundActionSummary>

    fun findAppIdsByUserAndRoundId(user: String, roundId: Int): List<AppRoundActionSummary>

    fun findByAppIdAndUserAndRoundId(
        appId: String,
        user: String,
        roundId: Int,
    ): AppRoundActionSummary?

    @Cacheable(value = ["app_round_countByAppIdAndRoundId"], key = "#appId + '-' + #roundId")
    fun countByAppIdAndRoundId(appId: String, roundId: Int): Long

    @Cacheable(
        value = ["app_round_countByTotalRewardAmountGreaterThanAndAppIdAndRoundId"],
        key = "#totalRewardAmount + '-' + #appId + '-' + #roundId",
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
    fun countByActionsRewardedGreaterThanAndAppIdAndRoundId(
        actionsRewarded: Long,
        appId: String,
        roundId: Int,
    ): Long
}
