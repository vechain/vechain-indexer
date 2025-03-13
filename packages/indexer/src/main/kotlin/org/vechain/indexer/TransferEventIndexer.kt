package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.TransferEventRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog
import org.vechain.indexer.utils.BlockUtils

@Profile("transfer-events")
@Component
open class TransferEventIndexer(
    private val transferEventRepository: TransferEventRepository,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.transfers}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.transfers}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.transfers}") private val syncBlockBatchSize: Long,
) :
    BaseLogIndexer(
        repository = transferEventRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT, LogType.TRANSFER),
        abiManager = abiManager,
    ) {
    override fun processLogs(
        events: List<EventLog>,
        transfers: List<TransferLog>,
    ) {
        val processedEvents =
            processAllEvents(
                events,
                transfers,
                FilterCriteria(
                    eventNames = listOf("Transfer", "TransferSingle", "TransferBatch"),
                ),
            )

        val transferEvents = BlockUtils.getAllTransferEvents(processedEvents)

        if (transferEvents.isNotEmpty()) {
            mongoTemplate.insert(transferEvents, IndexedTransferEvent::class.java)
        }
    }

    override fun rollback(blockNumber: Long) {
        transferEventRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
