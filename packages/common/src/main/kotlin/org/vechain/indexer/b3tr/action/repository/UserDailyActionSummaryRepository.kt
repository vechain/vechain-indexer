package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.action.UserDailyActionSummary
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface UserDailyActionSummaryRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(
        updated: List<UserDailyActionSummary>,
        existing: List<UserDailyActionSummary>,
    )

    // Query operations
    fun findAllByEntityAndDateBetween(
        entity: String,
        startDate: String,
        endDate: String,
        pageable: Pageable,
    ): Slice<UserDailyActionSummary>

    fun findAllByEntityTypeAndDate(
        entityType: EntityType,
        date: String,
        pageable: Pageable,
    ): Slice<UserDailyActionSummary>

    fun findByEntityAndDate(entity: String, date: String): UserDailyActionSummary?

    fun countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
        date: String,
    ): Long

    fun countByActionsRewardedGreaterThanAndEntityTypeAndDate(
        actionsRewarded: Long,
        entityType: EntityType,
        date: String,
    ): Long

    fun countByEntityTypeAndDate(entityType: EntityType, date: String): Long
}
