package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface AppAllTimeActionSummaryRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(
        updated: List<AppAllTimeActionSummary>,
        existing: List<AppAllTimeActionSummary>,
    )

    // Query operations
    fun findAllByAppId(appId: String, pageable: Pageable): Slice<AppAllTimeActionSummary>

    fun findAppIdsByUser(user: String): List<AppAllTimeActionSummary>

    fun findByAppIdAndUser(appId: String, user: String): AppAllTimeActionSummary?

    fun countByAppId(appId: String): Long

    fun countByTotalRewardAmountGreaterThanAndAppId(
        totalRewardAmount: BigDecimal,
        appId: String,
    ): Long

    fun countByActionsRewardedGreaterThanAndAppId(actionsRewarded: Long, appId: String): Long
}
