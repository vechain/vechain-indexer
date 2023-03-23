package org.vechain.indexer

import org.springframework.stereotype.Component
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.ContractUtils

@Component
class TransferEventIndexer(private val thorService: ThorService, private val transferEventRepo: TransferEventRepo, private val contractUtils: ContractUtils) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        var events: List<TransferEvent> = emptyList()
        block.transactions.forEach { tx ->
            if (!tx.reverted) {
                tx.outputs.forEachIndexed { index, txOutputs ->
                    val tfEvents = contractUtils.findTransferEvents(txOutputs.events)
                    if (tfEvents.isNotEmpty()) {
                        events = events.plus(
                                tfEvents.mapIndexed { evtIndex, it ->
                                    TransferEvent(
                                            "${tx.id}-${index}-${evtIndex}",
                                            block.id,
                                            block.number,
                                            tx.id,
                                            index,
                                            it.topics[1],
                                            it.topics[2],
                                            it.data,
                                            it.address
                                    )
                                }
                        )

                    }
                }
            }
        }


        if (events.isNotEmpty()) transferEventRepo.saveAll(events)
    }

    override fun getStartingBlock(): Long {
        return transferEventRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}