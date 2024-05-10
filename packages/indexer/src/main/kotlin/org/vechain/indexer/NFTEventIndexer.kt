package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
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
    private val archiveService: ArchiveService,
    thorClient: ThorClient,
    nftRepository: NFTRepository,
    @Value("\${indexer.startBlock.nfts}") private val startBlock: Long,
    @Value("\${indexer.syncLoggerInterval.nfts}") private val syncLoggerInterval: Long,
) :
    VeWorldIndexer(
        repository = nftRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLoggerInterval = syncLoggerInterval
    ) {

    override fun processBlock(block: Block) {

        // Get the NFT transfer events from the block
        val nftTransfers = BlockUtils.getNftTransferEventsFromTopics(block)
        if (nftTransfers.isEmpty()) return

        // Check for existing documents
        val existingNfts = nftService.getExisting(nftTransfers)

        // Parse the NFTs
        val nfts = parseNfts(block, nftTransfers, existingNfts)

        nftService.save(current = nfts, archived = existingNfts)
    }

    private fun parseNfts(
        block: Block,
        nftTransfers: List<IndexedTransferEvent>,
        existingNfts: List<IndexedNFT>
    ): List<IndexedNFT> {
        return nftTransfers.map {
            val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])
            val nftId = IdUtils.buildNftId(it)
            val version = existingNfts.find { nft -> nft.id == nftId }?.version?.plus(1) ?: 1

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

    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber, IndexedNFT::class.java)
    }
}
