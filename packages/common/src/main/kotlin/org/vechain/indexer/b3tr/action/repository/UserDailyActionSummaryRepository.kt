package org.vechain.indexer.b3tr.action.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.action.UserDailyActionSummary
import org.vechain.indexer.b3tr.shared.EntityType

@Profile("b3tr", "b3tr-actions", "b3tr-user-daily-action-summary")
@Repository
interface UserDailyActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<UserDailyActionSummary, String> {
    @Query("{ 'entity' : ?0, 'date' : { '\$gte' : ?1, '\$lte': ?2}}")
    fun findAllByEntityAndDateBetween(
        entity: String,
        startDate: String,
        endDate: String,
        pageable: Pageable,
    ): Slice<UserDailyActionSummary>

    fun findAllByEntityAndDateGreaterThanEqual(
        entity: String,
        startDate: String,
        pageable: Pageable,
    ): Slice<UserDailyActionSummary>

    fun findAllByEntityAndDateLessThanEqual(
        entity: String,
        endDate: String,
        pageable: Pageable,
    ): Slice<UserDailyActionSummary>

    fun findAllByEntity(entity: String, pageable: Pageable): Slice<UserDailyActionSummary>

    fun findByEntityAndDate(entity: String, date: String): UserDailyActionSummary?

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
