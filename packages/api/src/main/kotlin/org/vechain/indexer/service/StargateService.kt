package org.vechain.indexer.service

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.repository.stargate.VthoClaimedByAccountRepository
import org.vechain.indexer.repository.stargate.VthoClaimedByBlockRepository

@Profile("stargate")
@Service
open class StargateService(
    private val vthoClaimedByBlockRepository: VthoClaimedByBlockRepository,
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository,
) {

    /**
     * Retrieves the total VTHO claimed up to a specific block number. If no block number is
     * provided, it retrieves the latest total VTHO claimed.
     *
     * @param blockNumber The block number to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed as a BigInteger.
     */
    open fun getTotalVthoClaimed(blockNumber: Long?): BigInteger =
        (blockNumber?.let { vthoClaimedByBlockRepository.findLatestBeforeOrAtBlock(it) }
                ?: vthoClaimedByBlockRepository.getLatestRecord())
            ?.total ?: BigInteger.ZERO

    open fun getTotalVthoClaimed(account: String): BigInteger =
        vthoClaimedByAccountRepository.findById(account).map { it.total }.orElse(BigInteger.ZERO)
}
