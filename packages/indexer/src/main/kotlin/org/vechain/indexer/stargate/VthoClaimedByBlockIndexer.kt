package org.vechain.indexer.stargate

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseLogIndexer
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.stargate.VthoClaimedByBlock
import org.vechain.indexer.repository.stargate.VthoClaimedByBlockRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger

@Profile("stargate")
@Component
open class VthoClaimedByBlockIndexer(
    private val repository: VthoClaimedByBlockRepository,
    thorClient: ThorClient,
    abiManager: AbiManager,
    businessEventManager: BusinessEventManager,
    @Value("\${indexer.startBlock.stargate}") startBlock: Long,
    @Value("\${indexer.syncBlockBatchSize.stargate}") private val syncBlockBatchSize: Long,
    @Value("\${indexer.syncLogInterval.stargate}") private val logInterval: Long,
) :
    BaseLogIndexer(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = logInterval,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT, LogType.TRANSFER),
        abiManager = abiManager,
        businessEventManager = businessEventManager,
    ) {
    private val businessEventNames =
        listOf("STARGATE_CLAIM_REWARDS_BASE", "STARGATE_CLAIM_REWARDS_DELEGATE")

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        if (events.isEmpty()) {
            return
        }

        val genericEvents =
            processBlockGenericEvents(
                events,
                transfers,
                FilterCriteria(businessEventNames = businessEventNames),
            )

        val businessEvents =
            processBlockBusinessEvents(
                genericEvents,
                FilterCriteria(businessEventNames = businessEventNames),
            )

        if (businessEvents.isEmpty()) {
            return
        }

        processEvents(businessEvents)
    }

    override fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }

    /**
     * Parses the claim events to calculate the total VTHO claimed for each block, then creates a
     * new record by adding this value to the value on the latest record.
     *
     * @param events List of business events containing the claim data.
     */
    private fun processEvents(events: List<IndexedEvent>) {

        if (events.isEmpty()) {
            return
        }

        // Get the event with the largest block number
        val latestEvent = events.maxBy { it.blockNumber }

        // Calculate the total VTHO claimed for the events
        val totalVthoClaimed =
            events.sumOf { event -> event.params.getAsBigInteger("value") ?: BigInteger.ZERO }

        // Get the latest record from the repository
        val latestRecord = repository.getLatestRecord()

        // If there is no latest record, create a new one with the total VTHO claimed
        if (latestRecord == null) {
            repository.save(
                VthoClaimedByBlock(
                    blockId = latestEvent.blockId,
                    blockNumber = latestEvent.blockNumber,
                    blockTimestamp = latestEvent.blockTimestamp,
                    total = totalVthoClaimed,
                )
            )
            return
        }

        // If the block number of the latest record is larger than the latest block number from the
        // events, throw an error
        if (latestRecord.blockNumber >= latestEvent.blockNumber) {
            throw IllegalStateException(
                "Latest record block number ${latestRecord.blockNumber} is greater than or equal to the latest event block number ${latestEvent.blockNumber}"
            )
        }

        // Create a new record with the latest block number and the sum of the total VTHO claimed
        repository.save(
            VthoClaimedByBlock(
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                total = latestRecord.total + totalVthoClaimed,
            )
        )
    }
}
