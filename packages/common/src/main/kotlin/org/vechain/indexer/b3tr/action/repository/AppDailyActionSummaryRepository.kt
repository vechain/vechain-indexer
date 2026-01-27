package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.action.AppDailyActionSummary
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface AppDailyActionSummaryRepository :
    PostgresIndexedRepository, CustomAppDailyActionSummaryRepository {
    // Versioned operations
    fun saveAllVersioned(
        updated: List<AppDailyActionSummary>,
        existing: List<AppDailyActionSummary>,
    )

    // Query operations
    fun findAllByAppIdAndDate(
        appId: String,
        date: String,
        pageable: Pageable,
    ): Slice<AppDailyActionSummary>

    fun findByUserAndDate(user: String, date: String): List<AppDailyActionSummary>

    fun findByAppIdAndUserAndDate(appId: String, user: String, date: String): AppDailyActionSummary?

    fun countByAppIdAndDate(appId: String, date: String): Long

    fun countByTotalRewardAmountGreaterThanAndAppIdAndDate(
        totalRewardAmount: BigDecimal,
        appId: String,
        date: String,
    ): Long

    fun countByActionsRewardedGreaterThanAndAppIdAndDate(
        actionsRewarded: Long,
        appId: String,
        date: String,
    ): Long
}
