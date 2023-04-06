package org.vechain.indexer

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils
import org.web3j.utils.Numeric

@Profile("nft-event-indexer", "prod")
@Component
open class NFTEventIndexer(
    private val thorService: ThorService,
    private val nftRepo: NFTRepo
) : Indexer() {

    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)

        val transferEvents = BlockUtils.getTransferEvents(block)

        val nfts = getNfts(transferEvents)

        if (nfts.isNotEmpty()) nftRepo.saveAll(nfts)
    }

    private fun getNfts(transfers: List<TransferEvent>): List<NFT> {
        val nftTransfers = transfers.filter { it.isNFTTransfer }

        return nftTransfers.map {

            val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])

            NFT(
                id = buildHashedId("${it.tokenAddress}-${tokenId}"),
                owner = it.to,
                contractAddress = it.tokenAddress,
                tokenId = tokenId,
                txId = it.txId,
                blockNumber = it.blockNumber
            )
        }
    }


    override fun getStartingBlock(): Long {
        return nftRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

    private fun buildHashedId(plainId: String) = DigestUtils.sha1Hex(plainId)

}