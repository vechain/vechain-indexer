package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Projects the blacklist contract's events onto one state per (collection, block). */
@Profile("nfts", "history")
@Service
open class NftBlacklistService(private val repository: NftBlacklistWriteRepository) {
    companion object {
        const val NFT_BLACKLISTED = "NFTBlacklisted"
        const val NFT_WHITELISTED = "NFTWhitelisted"
        val EVENT_NAMES = listOf(NFT_BLACKLISTED, NFT_WHITELISTED)
    }

    /** The last event for a collection within a block decides its state at that block. */
    open fun processBlock(events: List<IndexedEvent>): List<NftBlacklistState> =
        groupByBlock(events.filter { it.eventType in EVENT_NAMES }).flatMap { (block, blockEvents)
            ->
            blockEvents
                .mapNotNull { event ->
                    event.params.getAsString("nft")?.let { nft ->
                        NftBlacklistState(
                            contractAddress = HexUtils.normalise(nft),
                            isBlacklisted = event.eventType == NFT_BLACKLISTED,
                            blockId = block.blockId,
                            blockNumber = block.blockNumber,
                            blockTimestamp = block.blockTimestamp,
                        )
                    }
                }
                .associateBy { it.contractAddress }
                .values
        }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(states: List<NftBlacklistState>) {
        if (states.isNotEmpty()) repository.save(states)
    }
}
