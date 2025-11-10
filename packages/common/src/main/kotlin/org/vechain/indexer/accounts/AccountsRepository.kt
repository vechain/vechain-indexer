package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("accounts")
interface AccountsRepository : BasePagingAndSortingIndexedRepository<Accounts, String> {
    fun findByTimeFrameIn(timeFrames: List<TimeFrame>, pageable: Pageable): Slice<Accounts>
}
