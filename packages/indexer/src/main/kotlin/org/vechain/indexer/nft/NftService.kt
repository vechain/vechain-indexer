package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.buildNftId

/** Projects `Transfer` events onto one ownership state per (token, block). */
@Profile("nfts")
@Service
open class NftService(private val repository: NftWriteRepository) {

    /** The last transfer of a token within a block decides its owner at that block. */
    open fun processBlock(events: List<IndexedEvent>): List<IndexedNft> =
        groupByBlock(events).flatMap { (_, blockEvents) ->
            blockEvents.map(::toNft).associateBy { it.id }.values
        }

    private fun toNft(event: IndexedEvent): IndexedNft =
        IndexedNft(
            id = buildNftId(event),
            owner = event.params.getAsString("to")!!,
            contractAddress = event.address ?: error("No contract address in event ${event.txId}"),
            tokenId = event.params.getAsString("tokenId")!!,
            txId = event.txId,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(nfts: List<IndexedNft>) {
        if (nfts.isNotEmpty()) repository.save(nfts)
    }
}
