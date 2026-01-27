package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.action.AppRoundActionSummary
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface AppRoundActionSummaryRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(
        updated: List<AppRoundActionSummary>,
        existing: List<AppRoundActionSummary>,
    )

    // Query operations
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

    fun countByAppIdAndRoundId(appId: String, roundId: Int): Long

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
}
