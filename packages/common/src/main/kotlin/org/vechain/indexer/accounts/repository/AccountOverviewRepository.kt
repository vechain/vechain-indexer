package org.vechain.indexer.accounts.repository

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.AccountOverview

@Profile("accounts", "account-overview")
interface AccountOverviewRepository : BaseIndexedRepository<AccountOverview, String> {}
