package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.sustainability.Overview

@Profile("b3tr", "sustainability", "sustainability-all")
@Repository
interface OverviewRepository : BasePagingAndSortingIndexedRepository<Overview, String> {
    // Count entries where totalRewardAmount is greater than a specific value, filtering by entity
    // type
    fun countByTotalRewardAmountGreaterThanAndEntityType(
        totalRewardAmount: Double,
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

    fun findByEntity(entity: String): Overview?

    fun findAllByEntity(entity: String, pageable: Pageable): Slice<Overview>

    fun findAllByEntityType(type: EntityType, pageable: Pageable): Slice<Overview>
}
