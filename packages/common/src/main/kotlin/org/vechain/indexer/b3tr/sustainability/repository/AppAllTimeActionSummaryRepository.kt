package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.sustainability.AppAllTimeActionSummary

@Profile("b3tr", "b3tr-sustainability", "b3tr-app-all-time-action-summary")
@Repository
interface AppAllTimeActionSummaryRepository :
    BasePagingAndSortingIndexedRepository<AppAllTimeActionSummary, String> {
    fun findAllByAppId(appId: String, pageable: Pageable): Slice<AppAllTimeActionSummary>

    fun findAllByUser(user: String, pageable: Pageable): Slice<AppAllTimeActionSummary>

    fun countByTotalRewardAmountGreaterThanAndAppId(totalRewardAmount: Double, appId: String): Long

    fun countByActionsRewardedGreaterThanAndAppId(actionsRewarded: Long, appId: String): Long

    fun findAppIdsByUser(user: String): List<AppAllTimeActionSummary>

    fun countByAppId(appId: String): Long
}
