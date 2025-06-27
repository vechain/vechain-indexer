package org.vechain.indexer.service.stargate

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.stargate.VetStakedByBlock
import org.vechain.indexer.repository.stargate.VetStakedByBlockRepository
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt

@Profile("stargate")
@Service
open class VetStakedByBlockService(private val repository: VetStakedByBlockRepository) {

    open fun processEvents(events: List<IndexedEvent>): VetStakedByBlock? {
        if (events.isEmpty()) return null

        val latestEvent = events.maxByOrNull { it.blockNumber } ?: return null
        val latestRecord = repository.getLatestRecord()

        var totalStaked = latestRecord?.total ?: BigInteger.ZERO
        val totalStakedPerLevel = (latestRecord?.byLevel?.toMutableMap() ?: mutableMapOf())

        for (event in events) {
            val amount = event.params.getAsBigInteger("value") ?: BigInteger.ZERO
            val levelId =
                event.params.getAsInt("levelId")
                    ?: throw IllegalArgumentException("Missing levelId in event params")

            when (event.eventType) {
                "STARGATE_STAKE",
                "STARGATE_DELEGATE" -> {
                    totalStaked += amount
                    totalStakedPerLevel[levelId] =
                        (totalStakedPerLevel[levelId] ?: BigInteger.ZERO) + amount
                }
                "STARGATE_UNSTAKE" -> {
                    totalStaked -= amount
                    totalStakedPerLevel[levelId] =
                        (totalStakedPerLevel[levelId] ?: BigInteger.ZERO) - amount
                }
            }
        }

        return VetStakedByBlock(
            blockId = latestEvent.blockId,
            blockNumber = latestEvent.blockNumber,
            blockTimestamp = latestEvent.blockTimestamp,
            total = totalStaked,
            byLevel = totalStakedPerLevel,
        )
    }

    open fun saveRecord(record: VetStakedByBlock) {
        repository.save(record)
    }

    open fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
