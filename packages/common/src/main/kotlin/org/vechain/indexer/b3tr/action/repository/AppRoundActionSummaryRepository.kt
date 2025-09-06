package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
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

    fun findAllByUserAndRoundId(
        user: String,
        roundId: Int,
        pageable: Pageable,
    ): Slice<AppRoundActionSummary>

    fun findAllByAppIdAndUser(
        appId: String,
        user: String,
        pageable: Pageable,
    ): Slice<AppRoundActionSummary>

    fun findByIdAndRoundId(id: String, roundId: Int): AppRoundActionSummary?

    fun countByTotalRewardAmountGreaterThanAndAppIdAndRoundId(
        totalRewardAmount: BigDecimal,
        appId: String,
        roundId: Int,
    ): Long

    fun countByActionsRewardedGreaterThanAndAppIdAndRoundId(
        actionsRewarded: Long,
        appId: String,
        roundId: Int,
    ): Long

    fun findAppIdsByUserAndRoundId(user: String, roundId: Int): List<AppRoundActionSummary>

    fun countByAppIdAndRoundId(appId: String, roundId: Int): Long
}
