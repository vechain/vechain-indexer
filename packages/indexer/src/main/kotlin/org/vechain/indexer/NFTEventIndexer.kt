package org.vechain.indexer

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils
import org.web3j.utils.Numeric

@Profile("nft-event-indexer", "prod")
@Component
open class NFTEventIndexer(
    thorService: ThorService,
    private val nftRepo: NFTRepo,
    mongoTemplate: MongoTemplate,
) : Indexer(thorService, nftRepo, mongoTemplate) {

    override fun processBlock(block: Block) {

        val transferEvents = BlockUtils.getTransferEventsFromTopics(block)

        val nfts = getNfts(transferEvents)

        if (nfts.isNotEmpty()) nftRepo.saveAll(nfts)
    }

    private fun getNfts(transfers: List<TransferEvent>): List<NFT> {
        return transfers.filter { it.eventType == TransferEventType.NFT && it.tokenAddress != null }
            .map {

                val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])

                NFT(
                    id = buildHashedId("${it.tokenAddress}-${tokenId}"),
                    owner = it.to,
                    contractAddress = it.tokenAddress!!,
                    tokenId = tokenId,
                    txId = it.txId,
                    blockId = it.blockId,
                    blockNumber = it.blockNumber,
                    blockTimestamp = it.blockTimestamp,
                )
            }
    }

    private fun buildHashedId(plainId: String) = DigestUtils.sha1Hex(plainId)

}