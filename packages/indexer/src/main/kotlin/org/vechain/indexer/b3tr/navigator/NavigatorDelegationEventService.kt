package org.vechain.indexer.b3tr.navigator

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorDelegationEventService(
    private val repository: NavigatorDelegationEventRepository
) {

    open fun processEvents(events: List<IndexedEvent>): List<NavigatorDelegationEvent> =
        events.map { ev ->
            val id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_${ev.eventType}")
            NavigatorDelegationEvent(
                id = id,
                blockId = ev.blockId,
                blockNumber = ev.blockNumber,
                blockTimestamp = ev.blockTimestamp,
                txId = ev.txId,
                navigator = ev.params.getAsString("navigator")?.lowercase() ?: "",
                citizen = ev.params.getAsString("citizen")?.lowercase() ?: "",
                eventType = ev.eventType,
                amount =
                    (ev.params.getAsString("amount") ?: ev.params.getAsString("newAmount"))
                        ?.toBigDecimalOrNull(),
            )
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<NavigatorDelegationEvent>) {
        if (records.isNotEmpty()) repository.saveAll(records)
    }
}
