package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.sustainability.AppRoundOverview

@Profile("b3tr", "sustainability", "sustainability-apps-rounds")
@Repository
interface AppRoundOverviewRepository :
    BasePagingAndSortingIndexedRepository<AppRoundOverview, String> {
    fun findFirstByOrderByBlockNumberDesc(): AppRoundOverview?

    fun findAllByAppIdAndRoundId(
        appId: String,
        roundId: Int,
        pageable: Pageable,
    ): Slice<AppRoundOverview>

    fun findAllByUserAndRoundId(
        user: String,
        roundId: Int,
        pageable: Pageable,
    ): Slice<AppRoundOverview>

    fun findAllByAppIdAndUser(
        appId: String,
        user: String,
        pageable: Pageable,
    ): Slice<AppRoundOverview>

    fun findByIdAndRoundId(id: String, roundId: Int): AppRoundOverview?

    fun countByTotalRewardAmountGreaterThanAndAppIdAndRoundId(
        totalRewardAmount: Double,
        appId: String,
        roundId: Int,
    ): Long

    fun countByActionsRewardedGreaterThanAndAppIdAndRoundId(
        actionsRewarded: Long,
        appId: String,
        roundId: Int,
    ): Long

    fun findAppIdsByUserAndRoundId(user: String, roundId: Int): List<AppRoundOverview>

    fun countByAppIdAndRoundId(appId: String, roundId: Int): Long
}
