package org.vechain.indexer.accounts.repository

import org.vechain.indexer.accounts.VetBalance
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface VetBalanceRepository : PostgresIndexedRepository {
    fun saveAll(records: List<VetBalance>)

    fun count(): Long

    fun findFirstByAddressOrderByBlockTimestampDesc(address: String): VetBalance?

    fun findByAddressAndBlockTimestampBetweenOrderByBlockTimestampDesc(
        address: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<VetBalance>
}
