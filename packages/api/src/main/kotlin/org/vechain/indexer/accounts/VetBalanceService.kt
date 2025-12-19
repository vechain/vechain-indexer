package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.thor.Address

@Profile("accounts")
@Service
open class VetBalanceService(private val vetBalanceRepository: VetBalanceRepository) {
    fun getByAddressInTimeRange(
        address: Address,
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<VetBalance> =
        vetBalanceRepository.findByAddressAndBlockTimestampBetweenOrderByBlockTimestampDesc(
            address = address.value,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
        )
}
