package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.groupByTokenId
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.processAllTokenEvents
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils.groupByBlock

/** Turns GM mint, transfer, upgrade and node events into each token's state per block. */
@Profile("b3tr", "b3tr-gm-nft")
@Service
open class GmNftService(private val repository: GmNftWriteRepository) {

    /** The new row of each token touched in each block, in ascending block order. */
    open fun processEvents(events: List<IndexedEvent>): List<GmNft> {
        if (events.isEmpty()) return emptyList()

        val current =
            repository
                .findCurrentByTokenIds(groupByTokenId(events).keys)
                .associateBy { it.tokenId }
                .toMutableMap()
        val rows = mutableListOf<GmNft>()

        groupByBlock(events).forEach { (_, blockEvents) ->
            groupByTokenId(blockEvents).forEach { (tokenId, tokenEvents) ->
                val existing = current[tokenId]
                val updated = processAllTokenEvents(existing, tokenEvents)
                if (existing != updated) {
                    current[tokenId] = updated
                    rows += updated
                }
            }
        }
        return rows
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(nfts: List<GmNft>) = repository.save(nfts)
}
