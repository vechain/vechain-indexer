package org.vechain.indexer.b3tr.navigator

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorFeeService(private val repository: NavigatorFeeRepository) {

    open fun processEvents(events: List<IndexedEvent>): List<NavigatorFee> =
        events.map { ev ->
            val id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_${ev.eventType}")
            NavigatorFee(
                id = id,
                blockId = ev.blockId,
                blockNumber = ev.blockNumber,
                blockTimestamp = ev.blockTimestamp,
                txId = ev.txId,
                navigator = ev.params.getAsString("navigator") ?: "",
                eventType = ev.eventType,
                roundId = ev.params.getAsString("roundId") ?: ev.params.getAsString("cycle") ?: "",
                amount = ev.params.getAsString("amount") ?: ev.params.getAsString("fee") ?: "0",
                citizen = ev.params.getAsString("citizen"),
            )
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<NavigatorFee>) {
        if (records.isNotEmpty()) repository.saveAll(records)
    }
}
