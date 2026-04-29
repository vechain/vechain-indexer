package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent

@Service
@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-delegation-event")
open class NavigatorDelegationEventService(
    private val repository: NavigatorDelegationEventRepository
) {

    open fun processEvents(events: List<IndexedEvent>): List<NavigatorDelegationEvent> =
        events.map { ev ->
            val id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_${ev.eventType}")

            when (ev.eventType) {
                "B3TR_DelegationCreated" -> {
                    ev.validateRequiredParams("citizen", "navigator", "amount")
                    val amount = ev.requireBigDecimalParam("amount")
                    buildEvent(ev, id, amount = amount, delta = amount)
                }
                "B3TR_DelegationIncreased" -> {
                    ev.validateRequiredParams("citizen", "navigator", "addedAmount", "newTotal")
                    val addedAmount = ev.requireBigDecimalParam("addedAmount")
                    val newTotal = ev.requireBigDecimalParam("newTotal")
                    buildEvent(ev, id, amount = newTotal, delta = addedAmount)
                }
                "B3TR_DelegationDecreased" -> {
                    ev.validateRequiredParams("citizen", "navigator", "removedAmount", "newTotal")
                    val removedAmount = ev.requireBigDecimalParam("removedAmount")
                    val newTotal = ev.requireBigDecimalParam("newTotal")
                    buildEvent(ev, id, amount = newTotal, delta = removedAmount.negate())
                }
                "B3TR_DelegationRemoved" -> {
                    ev.validateRequiredParams("citizen", "navigator", "amount")
                    val amount = ev.requireBigDecimalParam("amount")
                    buildEvent(ev, id, amount = BigDecimal.ZERO, delta = amount.negate())
                }
                else -> error("Unexpected event type: ${ev.eventType}")
            }
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<NavigatorDelegationEvent>) {
        if (records.isNotEmpty()) repository.saveAll(records)
    }

    private fun buildEvent(ev: IndexedEvent, id: String, amount: BigDecimal, delta: BigDecimal) =
        NavigatorDelegationEvent(
            id = id,
            blockId = ev.blockId,
            blockNumber = ev.blockNumber,
            blockTimestamp = ev.blockTimestamp,
            txId = ev.txId,
            navigator = ev.requireAddressParam("navigator"),
            citizen = ev.requireAddressParam("citizen"),
            eventType = ev.eventType,
            amount = amount,
            delta = delta,
        )
}
