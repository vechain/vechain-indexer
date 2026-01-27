package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface UserAllTimeActionSummaryRepository : PostgresIndexedRepository {
    // Versioned operations
    fun saveAllVersioned(
        updated: List<UserAllTimeActionSummary>,
        existing: List<UserAllTimeActionSummary>,
    )

    // Query operations
    fun findByEntity(entity: String): UserAllTimeActionSummary?

    fun findAllByEntityType(type: EntityType, pageable: Pageable): Slice<UserAllTimeActionSummary>

    fun countByTotalRewardAmountGreaterThanAndEntityType(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
    ): Long

    fun countByActionsRewardedGreaterThanAndEntityType(
        actionsRewarded: Long,
        entityType: EntityType,
    ): Long

    fun countByEntityType(entityType: EntityType): Long
}
