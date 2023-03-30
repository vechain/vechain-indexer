package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.ContractUtils
import org.web3j.utils.Numeric

@Profile("transfer-event-indexer", "prod")
@Component
open class TransferEventIndexer(
    private val thorService: ThorService,
    private val transferEventRepo: TransferEventRepo,
    private val nftRepo: NFTRepo
) : Indexer() {

    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)

        val allTransferEvents = getTransfers(block)
        val nfts = getNfts(allTransferEvents)

        if (nfts.isNotEmpty()) nftRepo.saveAll(nfts)
        if (allTransferEvents.isNotEmpty()) transferEventRepo.saveAll(allTransferEvents)
    }

    private fun getTransfers(block: Block): List<TransferEvent> {
        return block.transactions.filter { it.reverted == false }.flatMap {
            it.outputs.flatMap { output ->
                ContractUtils.findTransferEvents(output.events)
                    .mapIndexed { i, event ->
                        TransferEvent(
                            id = "${it.id}-${i}",
                            blockId = block.id,
                            blockNumber = block.number,
                            txId = it.id,
                            clauseIndex = 0,
                            from = ContractUtils.removeTopicPadding(event.topics[1]),
                            to = ContractUtils.removeTopicPadding(event.topics[2]),
                            value = event.data,
                            tokenAddress = event.address,
                            topics = event.topics
                        )
                    }
            }
        }
    }

    private fun getNfts(transfers: List<TransferEvent>): List<NFT> {
        val nftTransfers = transfers.filter { it.topics.size == 4 }

        return nftTransfers.map {

            val tokenId = Numeric.parsePaddedNumberHex(it.topics[3])

            NFT(
                id = "${it.tokenAddress}-${tokenId}",
                owner = it.to,
                contractAddress = it.tokenAddress,
                tokenId = tokenId,
                txId = it.txId,
                blockNumber = it.blockNumber
            )
        }
    }


    override fun getStartingBlock(): Long {

        val lastTransfersBlock = transferEventRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
        val lastNFTBlock = nftRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0

        return minOf(lastTransfersBlock, lastNFTBlock)
    }

}