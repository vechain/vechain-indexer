package org.vechain.indexer

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.NFTRepo
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.utils.BlockUtils
import org.vechain.thor.model.Block
import org.web3j.utils.Numeric

@Profile("nft-events")
@Component
open class NFTEventIndexer(
    private val nftRepo: NFTRepo,
    private val nftService: NFTService,
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${indexer.startBlock.nfts}") private val startBlock: Long,
) : VeWorldIndexer(nftRepo, thorUrl, startBlock) {

    @Transactional
    override fun processBlock(block: Block) {

        // Get the NFT transfer events from the block
        val nftTransfers = BlockUtils.getNFTTransferEventsFromTopics(block)
        if (nftTransfers.isEmpty()) return

        // Check for existing documents
        val existingNfts =
            nftRepo.findAllById(nftTransfers.map { buildHashedId("${it.tokenAddress}-${it.id}") }).toList()

        // Parse the NFTs
        val nfts = parseNfts(block, nftTransfers, existingNfts)

        // Save the NFTs and archive the old ones
        nftService.save(existingNfts)
        nftRepo.saveAll(nfts)
    }

    private fun parseNfts(
        block: Block,
        nftTransfers: List<IndexedTransferEvent>,
        existingNfts: List<IndexedNFT>
    ): List<IndexedNFT> {
        return nftTransfers.map {
            val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])
            val nftId = buildHashedId("${it.tokenAddress}-${tokenId}")
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
        val nfts = nftRepo.findAllByBlockNumber(blockNumber)

        //Get previous version of nfts
        val previousVersions = mutableSetOf<IndexedNFT>()
        nfts.forEach { nft ->
            if (nft.version > 1) {
                val previousVersion = nftService.getPreviousVersion(nft)
                previousVersions.add(previousVersion)
            }
        }

        // Remove nfts with version 1
        nftRepo.deleteAll(nfts.filter { it.version == 1 })

        // Save previous versions
        nftRepo.saveAll(previousVersions)
    }

    private fun buildHashedId(plainId: String) = DigestUtils.sha1Hex(plainId)

}
