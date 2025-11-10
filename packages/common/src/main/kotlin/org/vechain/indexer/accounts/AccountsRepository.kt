package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("accounts")
interface AccountsRepository : BasePagingAndSortingIndexedRepository<Accounts, String>
