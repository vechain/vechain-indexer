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
    @Value("\${indexer.pruner.removalChunkSize}") private val prunerRemovalChunkSize: Int,
    @Value("\${indexer.syncLogInterval.nfts}") private val syncLogInterval: Long
) :
    StatefulIndexer<IndexedNFT, NFTArchive>(
        repository = nftRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        archiveService = nftArchiveService,
        prunerRemovalChunkSize = prunerRemovalChunkSize
    ) {

    override fun processBlock(block: Block) {
        // Extract any relevant data from the block
        val data = BlockUtils.getNftTransferEventsFromTopics(block)
        if (data.isEmpty()) return

        // Find any existing records
        val existing = nftService.getExisting(data)

        // Process the updated records
        val updated = parseRecords(block, data, existing)

        // Finally save the updated records and archive the existing ones
        nftService.update(updated, existing)
    }

    private fun parseRecords(
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
