package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.IdUtils
import org.web3j.utils.Numeric

@Profile("nft-events")
@Component
open class NFTEventIndexer(
    private val nftService: NFTService,
    nftArchiveService: ArchiveService<IndexedNFT, NFTArchive>,
    thorClient: ThorClient,
    nftRepository: NFTRepository,
    @Value("\${indexer.startBlock.nfts}") private val startBlock: Long,
    @Value("\${indexer.syncLogInterval.nfts}") private val syncLogInterval: Long,
    @Value("\${indexer.pruner.enabled}") private val prunerEnabled: Boolean,
    @Value("\${indexer.pruner.interval}") private val prunerInterval: Long
) :
    StatefulIndexer<IndexedNFT, NFTArchive, IndexedTransferEvent>(
        repository = nftRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        prunerEnabled = prunerEnabled,
        prunerInterval = prunerInterval,
        archiveService = nftArchiveService
    ) {

    override fun extractData(block: Block): List<IndexedTransferEvent> {
        return BlockUtils.getNftTransferEventsFromTopics(block)
    }

    override fun findExisting(data: List<IndexedTransferEvent>): List<IndexedNFT> {
        return nftService.getExisting(data)
    }

    override fun parseRecords(
        block: Block,
        data: List<IndexedTransferEvent>,
        existing: List<IndexedNFT>
    ): List<IndexedNFT> {
        return data.map {
            val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])
            val nftId = IdUtils.buildNftId(it)
            val version = existing.find { nft -> nft.id == nftId }?.version?.plus(1) ?: 1

            IndexedNFT(
                id = nftId,
                version = version,
                owner = it.to,
                contractAddress = it.tokenAddress!!,
                tokenId = tokenId.toString(10),
                txId = it.txId,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )
        }
    }
}
