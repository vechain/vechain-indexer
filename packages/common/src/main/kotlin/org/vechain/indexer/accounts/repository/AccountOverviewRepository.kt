package org.vechain.indexer.accounts.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.AccountOverview

@Profile("accounts", "account-overview")
interface AccountOverviewRepository : BaseIndexedRepository<AccountOverview, String> {

    /**
     * Find accounts that need passive VTHO settlement at Hayabusa fork (paginated). These are
     * accounts with VET balance > 0 and lastVthoSettlement before the given timestamp.
     */
    @Query(
        "{ 'vetBalance': { '\$ne': '0' }, 'lastVthoSettlement': { '\$lt': ?0, '\$exists': true } }"
    )
    fun findAccountsNeedingVthoSettlement(
        beforeTimestamp: Long,
        pageable: Pageable,
    ): Slice<AccountOverview>
}
