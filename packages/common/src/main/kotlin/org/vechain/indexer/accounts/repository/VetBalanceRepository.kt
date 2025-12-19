package org.vechain.indexer.accounts.repository

import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.VetBalance

@Profile("accounts", "vet-balance")
interface VetBalanceRepository : BaseIndexedRepository<VetBalance, String> {
    fun findFirstByAddressOrderByBlockTimestampDesc(address: String): VetBalance?

    fun findByAddressAndBlockTimestampBetweenOrderByBlockTimestampDesc(
        address: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<VetBalance>
}
