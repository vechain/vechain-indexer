package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsInt

@Profile("stargate")
@Service
open class NftHoldersByBlockService(private val repository: NftHoldersByBlockRepository) {
    /**
     * Processes a list of events to calculate the total number of NFT holders and their
     * distribution by level.
     *
     * @param events List of IndexedEvent containing the events to process.
     * @return NftHoldersByBlock containing the total number of NFT holders and their distribution
     *   by level.
     */
    open fun processEvents(events: List<IndexedEvent>): NftHoldersByBlock? {
        if (events.isEmpty()) return null

        val latestEvent = events.maxByOrNull { it.blockNumber } ?: return null
        val latestRecord = repository.getLatestRecord()
        var totalNftHolders = latestRecord?.total ?: 0L
        val totalNftHoldersByLevel = (latestRecord?.byLevel ?: mutableMapOf()).toMutableMap()

        events.forEach { event ->
            val levelId =
                event.params.getAsInt("levelId")
                    ?: throw IllegalArgumentException("Missing levelId in event params")

            val level =
                TokenLevel.fromOrdinal(levelId)
                    ?: throw IllegalArgumentException("Invalid levelId: $levelId")

            when (event.eventType) {
                "STARGATE_STAKE" -> {
                    totalNftHolders += 1L
                    totalNftHoldersByLevel[level] =
                        totalNftHoldersByLevel.getOrDefault(level, 0L) + 1L
                }
                "STARGATE_UNSTAKE" -> {
                    totalNftHolders -= 1L
                    totalNftHoldersByLevel[level] =
                        totalNftHoldersByLevel.getOrDefault(level, 0L) - 1L
                }
            }
        }

        return NftHoldersByBlock(
            blockId = latestEvent.blockId,
            blockNumber = latestEvent.blockNumber,
            blockTimestamp = latestEvent.blockTimestamp,
            total = totalNftHolders,
            byLevel = totalNftHoldersByLevel,
        )
    }

    open fun saveRecord(record: NftHoldersByBlock) {
        repository.save(record)
    }
}
