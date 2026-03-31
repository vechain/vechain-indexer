package org.vechain.indexer.b3tr.navigator

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorDelegationService(private val repository: NavigatorDelegationRepository) {

    @Suppress("UNCHECKED_CAST")
    private fun getStringList(ev: IndexedEvent, key: String): List<String>? {
        val raw = ev.params.getAsString(key) ?: return null
        if (raw.isBlank() || raw == "[]") return null
        return raw.removePrefix("[").removeSuffix("]").split(",").map { it.trim() }
    }

    open fun processEvents(events: List<IndexedEvent>): List<NavigatorDelegation> =
        events.map { ev ->
            val id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_${ev.eventType}")
            NavigatorDelegation(
                id = id,
                blockId = ev.blockId,
                blockNumber = ev.blockNumber,
                blockTimestamp = ev.blockTimestamp,
                txId = ev.txId,
                citizen = ev.params.getAsString("citizen") ?: "",
                navigator = ev.params.getAsString("navigator") ?: "",
                eventType = ev.eventType,
                amount = ev.params.getAsString("amount") ?: ev.params.getAsString("newAmount"),
                roundId = ev.params.getAsString("roundId"),
                appIds = getStringList(ev, "appsIds"),
                voteWeights = getStringList(ev, "voteWeights"),
            )
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<NavigatorDelegation>) {
        if (records.isNotEmpty()) repository.saveAll(records)
    }
}
