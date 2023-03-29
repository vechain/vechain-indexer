package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.exception.IndexerSynchronizationException
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.ContractUtils

@Profile("transfer-event-indexer", "prod")
@Component
open class TransferEventIndexer(
    private val thorService: ThorService,
    private val transferEventRepo: TransferEventRepo,
    private val contractUtils: ContractUtils,
    private val contractIndexer: ContractIndexer,
) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        var events: List<TransferEvent> = emptyList()

        ensureInSyncWithContracts(block.number)

        block.transactions.forEach { tx ->
            if (tx.reverted != false) {
                tx.outputs.forEachIndexed { index, txOutputs ->
                    val tfEvents = contractUtils.findTransferEvents(txOutputs.events)
                    if (tfEvents.isNotEmpty()) {
                        events = events.plus(
                            tfEvents.mapIndexed { evtIndex, it ->
                                TransferEvent(
                                    id = "${tx.id}-${index}-${evtIndex}",
                                    blockId = block.id,
                                    blockNumber = block.number,
                                    txId = tx.id,
                                    clauseIndex = index,
                                    from = contractUtils.removeTopicPadding(it.topics[1]),
                                    to = contractUtils.removeTopicPadding(it.topics[2]),
                                    value = it.data,
                                    tokenAddress = it.address
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

    private fun ensureInSyncWithContracts(eventsBlockNumber: Long) {
        val contractsLastBlock = contractIndexer.getCurrentBlock()
        if (eventsBlockNumber > contractIndexer.getCurrentBlock()) {
            throw IndexerSynchronizationException("Waiting for contracts indexer at block: $contractsLastBlock. Currently at $eventsBlockNumber")
        }
    }

}