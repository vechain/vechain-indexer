package org.vechain.indexer.accounts.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.accounts.TotalAccounts

@Profile("accounts", "total-accounts")
interface TotalAccountsRepository : BasePagingAndSortingIndexedRepository<TotalAccounts, String> {
    fun findByTimeFrameIn(timeFrames: List<TimeFrame>, pageable: Pageable): Slice<TotalAccounts>
}
