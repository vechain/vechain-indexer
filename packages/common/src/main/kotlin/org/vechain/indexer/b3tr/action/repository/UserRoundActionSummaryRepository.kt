package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.action.UserRoundActionSummary
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface UserRoundActionSummaryRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(
        updated: List<UserRoundActionSummary>,
        existing: List<UserRoundActionSummary>,
    )

    // Query operations
    fun findFirstByOrderByBlockNumberDesc(): UserRoundActionSummary?

    fun findAllByEntityTypeAndRoundId(
        entityType: EntityType,
        roundId: Int,
        pageable: Pageable,
    ): Slice<UserRoundActionSummary>

    fun findByEntityAndRoundId(entity: String, roundId: Int): UserRoundActionSummary?

    fun countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
        roundId: Int,
    ): Long

    fun countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
        actionsRewarded: Long,
        entityType: EntityType,
        roundId: Int,
    ): Long

    fun countByEntityTypeAndRoundId(entityType: EntityType, roundId: Int): Long
}
