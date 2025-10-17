package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.action.UserRoundActionSummary
import org.vechain.indexer.b3tr.shared.EntityType

@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
@Repository
interface UserRoundActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<UserRoundActionSummary, String> {
    fun findFirstByOrderByBlockNumberDesc(): UserRoundActionSummary?

    fun findAllByEntityTypeAndRoundId(
        entityType: EntityType,
        roundId: Int,
        pageable: Pageable,
    ): Slice<UserRoundActionSummary>

    fun findByEntityAndRoundId(entity: String, roundId: Int): UserRoundActionSummary?

    // Count entries where totalRewardAmount is greater than a specific value, filtering by entity
    // type and round ID
    @Cacheable(
        value = ["user_round_countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId"],
        key = "#totalRewardAmount + '-' + #entityType + '-' + #roundId",
    )
    fun countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
        roundId: Int,
    ): Long

    // Count entries where actionsRewarded is greater than a specific value, filtering by entity
    // type and round ID
    @Cacheable(
        value = ["user_round_countByActionsRewardedGreaterThanAndEntityTypeAndRoundId"],
        key = "#actionsRewarded + '-' + #entityType + '-' + #roundId",
    )
    fun countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
        actionsRewarded: Long,
        entityType: EntityType,
        roundId: Int,
    ): Long

    // Count entries filtering by entity type and round ID
    @Cacheable(
        value = ["user_round_countByEntityTypeAndRoundId"],
        key = "#entityType + '-' + #roundId",
    )
    fun countByEntityTypeAndRoundId(entityType: EntityType, roundId: Int): Long
}
