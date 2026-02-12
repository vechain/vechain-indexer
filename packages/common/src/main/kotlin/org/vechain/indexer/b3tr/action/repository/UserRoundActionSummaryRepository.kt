package org.vechain.indexer.b3tr.action.repository

import java.math.BigDecimal
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.b3tr.action.UserRoundActionSummary
import org.vechain.indexer.b3tr.shared.EntityType

@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
@Repository
interface UserRoundActionSummaryRepository : BaseIndexedRepository<UserRoundActionSummary, String> {
    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun findFirstByOrderByBlockNumberDesc(): UserRoundActionSummary?

    @Query("{ 'entityType': ?0, 'roundId': ?1 }")
    fun findAllByEntityTypeAndRoundId(
        entityType: EntityType,
        roundId: Int,
        pageable: Pageable,
    ): Slice<UserRoundActionSummary>

    @Query("{ 'entity': ?0, 'roundId': ?1 }")
    fun findByEntityAndRoundId(entity: String, roundId: Int): UserRoundActionSummary?

    // Count entries where totalRewardAmount is greater than a specific value, filtering by entity
    // type and round ID
    @Cacheable(
        value = ["user_round_countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId"],
        key = "#totalRewardAmount + '-' + #entityType + '-' + #roundId",
    )
    @Query(
        value = "{ 'totalRewardAmount': { '\$gt': ?0 }, 'entityType': ?1, 'roundId': ?2 }",
        count = true,
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
    @Query(
        value = "{ 'actionsRewarded': { '\$gt': ?0 }, 'entityType': ?1, 'roundId': ?2 }",
        count = true,
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
    @Query(value = "{ 'entityType': ?0, 'roundId': ?1 }", count = true)
    fun countByEntityTypeAndRoundId(entityType: EntityType, roundId: Int): Long
}
