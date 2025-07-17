package org.vechain.indexer.stargate

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.stargate.VthoClaimedByBlock
import org.vechain.indexer.repository.stargate.VthoClaimedByBlockRepository
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger

@Profile("stargate")
@Service
open class VthoClaimedByBlockService(private val repository: VthoClaimedByBlockRepository) {

    open fun processEvents(events: List<IndexedEvent>): VthoClaimedByBlock? {
        if (events.isEmpty()) return null

        val latestEvent = events.maxByOrNull { it.blockNumber } ?: return null
        val totalVthoClaimed =
            events.sumOf { it.params.getAsBigInteger("value") ?: BigInteger.ZERO }
        val latestRecord = repository.getLatestRecord()

        if (latestRecord == null) {
            return VthoClaimedByBlock(
                blockId = latestEvent.blockId,
                blockNumber = latestEvent.blockNumber,
                blockTimestamp = latestEvent.blockTimestamp,
                total = totalVthoClaimed,
            )
        }

        if (latestRecord.blockNumber >= latestEvent.blockNumber) {
            throw IllegalStateException(
                "Latest record block number ${latestRecord.blockNumber} is greater than or equal to the latest event block number ${latestEvent.blockNumber}"
            )
        }

        return VthoClaimedByBlock(
            blockId = latestEvent.blockId,
            blockNumber = latestEvent.blockNumber,
            blockTimestamp = latestEvent.blockTimestamp,
            total = latestRecord.total + totalVthoClaimed,
        )
    }

    open fun saveRecord(record: VthoClaimedByBlock) {
        repository.save(record)
    }
}
