package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.b3tr.shared.EntityType

@Profile("b3tr", "b3tr-actions", "b3tr-user-all-time-action-summary")
@Repository
interface UserAllTimeActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<UserAllTimeActionSummary, String> {
    // Count entries where totalRewardAmount is greater than a specific value, filtering by entity
    // type
    fun countByTotalRewardAmountGreaterThanAndEntityType(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
    ): Long

    // Count entries where actionsRewarded is greater than a specific value, filtering by entity
    // type
    fun countByActionsRewardedGreaterThanAndEntityType(
        actionsRewarded: Long,
        entityType: EntityType,
    ): Long

    // Count entries where entity is equal to a specific value
    fun countByEntityType(entityType: EntityType): Long

    fun findByEntity(entity: String): UserAllTimeActionSummary?

    fun findAllByEntity(entity: String, pageable: Pageable): Slice<UserAllTimeActionSummary>

    fun findAllByEntityType(type: EntityType, pageable: Pageable): Slice<UserAllTimeActionSummary>
}
