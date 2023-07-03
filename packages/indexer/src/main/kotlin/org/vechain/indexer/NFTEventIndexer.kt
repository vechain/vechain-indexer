package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.IdUtils
import org.vechain.thor.model.Block
import org.web3j.utils.Numeric

@Profile("nft-events")
@Component
open class NFTEventIndexer(
    private val nftRepository: NFTRepository,
    private val archiveService: ArchiveService,
    thorClient: ThorClient,
    @Value("\${indexer.startBlock.nfts}") private val startBlock: Long,
) : VeWorldIndexer(repository = nftRepository, startBlock = startBlock, thorClient = thorClient) {

    @Transactional
    override fun processBlock(block: Block) {

        // Get the NFT transfer events from the block
        val nftTransfers = BlockUtils.getNFTTransferEventsFromTopics(block)
        if (nftTransfers.isEmpty()) return

        // Check for existing documents
        val existingNfts =
            nftRepository
                .findAllById(
                    nftTransfers.map { IdUtils.buildHashedId("${it.tokenAddress}-${it.id}") }
                )
                .toList()

        // Parse the NFTs
        val nfts = parseNfts(block, nftTransfers, existingNfts)

        // Save the NFTs and archive the old ones
        if (existingNfts.isNotEmpty()) archiveService.saveAll(existingNfts)
        nftRepository.saveAll(nfts)
    }

    private fun parseNfts(
        block: Block,
        nftTransfers: List<IndexedTransferEvent>,
        existingNfts: List<IndexedNFT>
    ): List<IndexedNFT> {
        return nftTransfers.map {
            val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])
            val nftId = IdUtils.buildHashedId("${it.tokenAddress}-${tokenId}")
            val version = (existingNfts.find { nft -> nft.id == nftId }?.version?.plus(1)) ?: 1

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

    @Transactional
    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber, IndexedNFT::class.java)
    }
}
