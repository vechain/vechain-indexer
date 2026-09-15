package org.vechain.indexer.safe

import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

/** The Safe an event belongs to: its own log address, or the emitter's indexed `safe` param. */
object SafeEventUtils {
    const val PROXY_CREATION = "ProxyCreation"

    val MEMBERSHIP_EVENTS = setOf("SafeSetup", "AddedOwner", "RemovedOwner")
    val TX_STATE_EVENTS = setOf("ApproveHash", "ExecutionSuccess", "ExecutionFailure")
    val PROPOSAL_EVENTS = setOf("SafeTxProposed", "SafeTxHashFields", "SafeBatchTxProposed")

    fun safeOf(event: IndexedEvent): String? =
        when (event.eventType) {
            in MEMBERSHIP_EVENTS,
            in TX_STATE_EVENTS -> event.address?.let { HexUtils.normalise(it) }
            in PROPOSAL_EVENTS -> event.params.getAsString("safe")?.let { HexUtils.normalise(it) }
            else -> null
        }

    /** Every address an entry claims is a Safe, to be checked against the deployed proxies. */
    fun candidateSafes(events: List<IndexedEvent>): Set<String> =
        events.mapNotNull(::safeOf).toSet()

    fun addressParam(event: IndexedEvent, name: String): String? =
        event.params.getAsString(name)?.takeIf { it.isNotBlank() }?.let { HexUtils.normalise(it) }
}
