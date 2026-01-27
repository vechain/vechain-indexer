package org.vechain.indexer.accounts.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.accounts.TotalAccounts
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface TotalAccountsRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<TotalAccounts>, existing: List<TotalAccounts>)

    fun findById(id: String): TotalAccounts?

    fun findAllById(ids: Collection<String>): List<TotalAccounts>

    fun findByTimeFrameIn(timeFrames: List<TimeFrame>, pageable: Pageable): Slice<TotalAccounts>
}
