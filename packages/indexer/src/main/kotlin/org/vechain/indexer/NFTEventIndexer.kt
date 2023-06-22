package org.vechain.indexer

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.utils.BlockUtils
import org.vechain.thor.model.Block
import org.web3j.utils.Numeric

@Profile("nft-events")
@Component
open class NFTEventIndexer(
    private val nftRepository: NFTRepository,
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${indexer.startBlock.nfts}") private val startBlock: Long,

    ) : VeWorldIndexer(nftRepository, thorUrl, startBlock) {

    override fun processBlock(block: Block) {

        val transferEvents = BlockUtils.getTransferEventsFromTopics(block)

        val nfts = getNfts(transferEvents)

        if (nfts.isNotEmpty()) nftRepository.saveAll(nfts)
    }

    private fun getNfts(transfers: List<IndexedTransferEvent>): List<IndexedNFT> {
        return transfers.filter { it.eventType == TransferEventType.NFT && it.tokenAddress != null }
            .map {

                val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])

                IndexedNFT(
                    id = buildHashedId("${it.tokenAddress}-${tokenId}"),
                    owner = it.to,
                    contractAddress = it.tokenAddress!!,
                    tokenId = tokenId.toString(10),
                    txId = it.txId,
                    blockId = it.blockId,
                    blockNumber = it.blockNumber,
                    blockTimestamp = it.blockTimestamp,
                )
            }
    }

    private fun buildHashedId(plainId: String) = DigestUtils.sha1Hex(plainId)

}
