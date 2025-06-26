package org.vechain.indexer.service

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.repository.stargate.TotalVthoClaimedByAccountRepository
import org.vechain.indexer.repository.stargate.TotalVthoClaimedByBlockRepository

@Profile("stargate")
@Service
open class StargateService(
    private val totalVthoClaimedByBlockRepository: TotalVthoClaimedByBlockRepository,
    private val totalVthoClaimedByAccountRepository: TotalVthoClaimedByAccountRepository,
) {

    /**
     * Retrieves the total VTHO claimed up to a specific block number. If no block number is
     * provided, it retrieves the latest total VTHO claimed.
     *
     * @param blockNumber The block number to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed as a BigInteger.
     */
    open fun getTotalVthoClaimed(blockNumber: Long?): BigInteger =
        (blockNumber?.let { totalVthoClaimedByBlockRepository.findLatestBeforeOrAtBlock(it) }
                ?: totalVthoClaimedByBlockRepository.getLatestRecord())
            ?.value ?: BigInteger.ZERO

    open fun getTotalVthoClaimed(account: String): BigInteger =
        totalVthoClaimedByAccountRepository
            .findById(account)
            .map { it.value }
            .orElse(BigInteger.ZERO)
}
