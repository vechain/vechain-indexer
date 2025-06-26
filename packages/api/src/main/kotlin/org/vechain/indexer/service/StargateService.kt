package org.vechain.indexer.service

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.model.stargate.NftHoldersByBlock
import org.vechain.indexer.model.stargate.VetStakedByBlock
import org.vechain.indexer.repository.stargate.NftHoldersByBlockRepository
import org.vechain.indexer.repository.stargate.VetStakedByBlockRepository
import org.vechain.indexer.repository.stargate.VthoClaimedByAccountRepository
import org.vechain.indexer.repository.stargate.VthoClaimedByBlockRepository

@Profile("stargate")
@Service
open class StargateService(
    private val vthoClaimedByBlockRepository: VthoClaimedByBlockRepository,
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository,
    private val nftHoldersByBlockRepository: NftHoldersByBlockRepository,
    private val vetStakedByBlockRepository: VetStakedByBlockRepository,
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

    /**
     * Retrieves the total VTHO claimed by a specific account.
     *
     * @param account The account address to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed by the account as a BigInteger.
     */
    open fun getTotalVthoClaimed(account: String): BigInteger =
        vthoClaimedByAccountRepository.findById(account).map { it.total }.orElse(BigInteger.ZERO)

    /**
     * Retrieves the total number of NFT holders in Stargate at a specific block number. If no block
     * number is provided, it retrieves the latest total number of NFT holders.
     *
     * @param blockNumber The block number to retrieve the total NFT holders for.
     * @return The total number of NFT holders as an instance of NftHoldersByBlock or null if no
     *   data is found.
     */
    open fun getNftHolders(blockNumber: Long?): NftHoldersByBlock? =
        (blockNumber?.let { nftHoldersByBlockRepository.findLatestBeforeOrAtBlock(it) }
            ?: nftHoldersByBlockRepository.getLatestRecord())

    /**
     * Retrieves the total VET staked in Stargate at a specific block number. If no block number is
     * provided, it retrieves the latest total VET staked.
     *
     * @param blockNumber The block number to retrieve the total VET staked for.
     * @return The total VET staked as an instance of VetStakedByBlock or null if no data is found.
     */
    fun getTotalVetStaked(blockNumber: Long?): VetStakedByBlock? =
        (blockNumber?.let { vetStakedByBlockRepository.findLatestBeforeOrAtBlock(it) }
            ?: vetStakedByBlockRepository.getLatestRecord())
}
