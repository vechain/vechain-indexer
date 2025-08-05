package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.sustainability.RoundOverview

@Profile("b3tr", "sustainability", "sustainability-rounds")
@Repository
interface RoundOverviewRepository : BasePagingAndSortingIndexedRepository<RoundOverview, String> {
    fun findFirstByOrderByBlockNumberDesc(): RoundOverview?

    fun findByEntityAndRoundId(entity: String, roundId: Int): RoundOverview?

    fun findAllByEntityAndRoundId(
        entity: String,
        roundId: Int,
        pageable: Pageable,
    ): Slice<RoundOverview>

    fun findAllByEntityOrderByRoundIdDesc(entity: String): List<RoundOverview>

    fun findAllByEntity(entity: String, pageable: Pageable): Slice<RoundOverview>

    fun findAllByRoundIdAndEntityType(
        roundId: Int,
        type: EntityType,
        pageable: Pageable,
    ): Slice<RoundOverview>

    // Count entries where totalRewardAmount is greater than a specific value, filtering by entity
    // type and round ID
    fun countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
        totalRewardAmount: Double,
        entityType: EntityType,
        roundId: Int,
    ): Long

    // Count entries where actionsRewarded is greater than a specific value, filtering by entity
    // type and round ID
    fun countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
        actionsRewarded: Long,
        entityType: EntityType,
        roundId: Int,
    ): Long

    fun countByEntityTypeAndRoundId(entityType: EntityType, roundId: Int): Long
}
