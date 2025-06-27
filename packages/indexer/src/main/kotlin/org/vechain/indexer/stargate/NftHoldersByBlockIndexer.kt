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
import org.vechain.indexer.model.stargate.NftHoldersByBlock
import org.vechain.indexer.repository.stargate.NftHoldersByBlockRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog
import org.vechain.indexer.utils.ParamUtils.getAsInt

@Profile("stargate")
@Component
open class NftHoldersByBlockIndexer(
    private val repository: NftHoldersByBlockRepository,
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
        listOf("STARGATE_STAKE", "STARGATE_DELEGATE", "STARGATE_UNSTAKE")

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

    private fun processEvents(events: List<IndexedEvent>) {

        if (events.isEmpty()) {
            return
        }

        // Get the event with the largest block number
        val latestEvent = events.maxBy { it.blockNumber }

        // Get the latest record from the repository
        val latestRecord = repository.getLatestRecord()

        var totalNftHolders = latestRecord?.total ?: BigInteger.ZERO
        val totalNftHoldersByLevel = (latestRecord?.byLevel ?: mutableMapOf()).toMutableMap()

        // Iterate through the events to calculate the total staked amount
        for (event in events) {
            val levelId =
                event.params.getAsInt("levelId")
                    ?: throw IllegalArgumentException("Missing levelId in event params")

            when (event.eventType) {
                "STARGATE_STAKE",
                "STARGATE_DELEGATE" -> {
                    totalNftHolders += BigInteger.ONE
                    totalNftHoldersByLevel[levelId] =
                        (totalNftHoldersByLevel[levelId] ?: BigInteger.ZERO) + BigInteger.ONE
                }
                "STARGATE_UNSTAKE" -> {
                    totalNftHolders -= BigInteger.ONE
                    totalNftHoldersByLevel[levelId] =
                        (totalNftHoldersByLevel[levelId] ?: BigInteger.ZERO) - BigInteger.ONE
                }
            }
        }

        // Store the new record in the repository
        repository.save(
            NftHoldersByBlock(
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                total = totalNftHolders,
                byLevel = totalNftHoldersByLevel,
            )
        )
    }
}
