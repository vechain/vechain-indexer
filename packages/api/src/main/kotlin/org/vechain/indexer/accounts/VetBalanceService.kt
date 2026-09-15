package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address

@Profile("accounts")
@Service
open class VetBalanceService(private val repository: VetBalanceReadRepository) {
    fun getByAddressInTimeRange(
        address: Address,
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<VetBalance> =
        repository.findByAddressBetween(address.value, startTimestamp, endTimestamp)
}
