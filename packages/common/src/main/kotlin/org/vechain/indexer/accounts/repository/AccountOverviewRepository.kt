package org.vechain.indexer.accounts.repository

import org.vechain.indexer.accounts.AccountOverview
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface AccountOverviewRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<AccountOverview>, existing: List<AccountOverview>)

    fun findByAddress(address: String): AccountOverview?
}
