package org.vechain.indexer.b3tr.navigator

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorEventService(private val repository: NavigatorEventRepository) {

    open fun processEvents(events: List<IndexedEvent>): List<NavigatorEvent> =
        events.map { ev ->
            val id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_${ev.eventType}")
            NavigatorEvent(
                id = id,
                blockId = ev.blockId,
                blockNumber = ev.blockNumber,
                blockTimestamp = ev.blockTimestamp,
                txId = ev.txId,
                navigator = ev.params.getAsString("navigator") ?: "",
                eventType = ev.eventType,
                stakeAmount =
                    ev.params.getAsString("stakeAmount") ?: ev.params.getAsString("amount"),
                metadataURI =
                    ev.params.getAsString("metadataURI") ?: ev.params.getAsString("newURI"),
                slashAmount = ev.params.getAsString("amount"),
                slashReason = ev.params.getAsString("reason"),
                remainingStake =
                    ev.params.getAsString("remainingStake") ?: ev.params.getAsString("remaining"),
                announcedAtRound = ev.params.getAsString("announcedAtRound"),
                effectiveRound = ev.params.getAsString("effectiveRound"),
                reportRoundId = ev.params.getAsString("roundId"),
                reportURI = ev.params.getAsString("reportURI"),
            )
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<NavigatorEvent>) {
        if (records.isNotEmpty()) repository.saveAll(records)
    }
}
