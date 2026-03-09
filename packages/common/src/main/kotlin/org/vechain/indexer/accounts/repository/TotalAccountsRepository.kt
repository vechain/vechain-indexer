package org.vechain.indexer.accounts.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.accounts.TotalAccounts

@Profile("accounts", "total-accounts")
@Deprecated(
    "V1 total-accounts repository is deprecated. Use AccountTotalsSeriesRepository instead."
)
interface TotalAccountsRepository : BaseIndexedRepository<TotalAccounts, String> {
    @Query("{ 'timeFrame': { '\$in': ?0 } }")
    fun findByTimeFrameIn(timeFrames: List<TimeFrame>, pageable: Pageable): Slice<TotalAccounts>
}
