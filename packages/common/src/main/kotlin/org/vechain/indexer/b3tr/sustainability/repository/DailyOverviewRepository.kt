package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.b3tr.sustainability.DailyOverview

@Profile("b3tr", "sustainability", "sustainability-daily")
@Repository
interface DailyOverviewRepository : BasePagingAndSortingIndexedRepository<DailyOverview, String> {
    @Query("{ 'entity' : ?0, 'date' : { '\$gte' : ?1, '\$lte': ?2}}")
    fun findAllByEntityAndDateBetween(
        entity: String,
        startDate: String,
        endDate: String,
        pageable: Pageable,
    ): Slice<DailyOverview>

    fun findAllByEntityAndDateGreaterThanEqual(
        entity: String,
        startDate: String,
        pageable: Pageable,
    ): Slice<DailyOverview>

    fun findAllByEntityAndDateLessThanEqual(
        entity: String,
        endDate: String,
        pageable: Pageable,
    ): Slice<DailyOverview>

    fun findAllByEntity(entity: String, pageable: Pageable): Slice<DailyOverview>

    fun findByEntityAndDate(entity: String, date: String): DailyOverview?

    fun countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
        totalRewardAmount: Double,
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
