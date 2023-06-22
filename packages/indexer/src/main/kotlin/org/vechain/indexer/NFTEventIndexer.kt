package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.IdUtils
import org.vechain.thor.model.Block
import org.web3j.utils.Numeric

@Profile("nft-events")
@Component
open class NFTEventIndexer(
    private val nftRepository: NFTRepository,
    private val nftService: NFTService,
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${indexer.startBlock.nfts}") private val startBlock: Long,
) : VeWorldIndexer(nftRepository, thorUrl, startBlock) {

    @Transactional
    override fun processBlock(block: Block) {

        // Get the NFT transfer events from the block
        val nftTransfers = BlockUtils.getNFTTransferEventsFromTopics(block)
        if (nftTransfers.isEmpty()) return

        // Check for existing documents
        val existingNfts =
            nftRepository.findAllById(nftTransfers.map { IdUtils.buildHashedId("${it.tokenAddress}-${it.id}") })
                .toList()

        // Parse the NFTs
        val nfts = parseNfts(block, nftTransfers, existingNfts)

        // Save the NFTs and archive the old ones
        if (existingNfts.isNotEmpty()) nftService.save(existingNfts)
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
        //Get all nfts that were created in the block
        val nfts = nftRepository.findAllByBlockNumber(blockNumber)

        //Get previous version of nfts
        val previousVersions = mutableSetOf<IndexedNFT>()
        nfts.forEach { nft ->
            if (nft.version > 1) {
                val previousVersion = nftService.getPreviousVersion(nft)
                previousVersions.add(previousVersion)
            }
        }

        // Remove nfts with version 1
        nftRepository.deleteAll(nfts.filter { it.version == 1 })

        // Save previous versions
        nftRepository.saveAll(previousVersions)
    }
}
