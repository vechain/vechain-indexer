package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_CREATED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_DECREASED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_EVENTS
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_INCREASED
import org.vechain.indexer.event.model.generic.IndexedEvent

/** One append-only row per delegation event, with the delegation after it and what moved. */
@Profile("b3tr", "b3tr-navigator")
@Service
open class NavigatorDelegationEventService {

    open fun processEvents(events: List<IndexedEvent>): List<NavigatorDelegationEvent> =
        events
            .filter { it.eventType in DELEGATION_EVENTS }
            .map { ev ->
                when (ev.eventType) {
                    DELEGATION_CREATED -> {
                        ev.validateRequiredParams("citizen", "navigator", "amount")
                        val amount = ev.requireBigDecimalParam("amount")
                        buildEvent(ev, amount = amount, delta = amount)
                    }
                    DELEGATION_INCREASED -> {
                        ev.validateRequiredParams("citizen", "navigator", "addedAmount", "newTotal")
                        buildEvent(
                            ev,
                            amount = ev.requireBigDecimalParam("newTotal"),
                            delta = ev.requireBigDecimalParam("addedAmount"),
                        )
                    }
                    DELEGATION_DECREASED -> {
                        ev.validateRequiredParams(
                            "citizen",
                            "navigator",
                            "removedAmount",
                            "newTotal",
                        )
                        buildEvent(
                            ev,
                            amount = ev.requireBigDecimalParam("newTotal"),
                            delta = ev.requireBigDecimalParam("removedAmount").negate(),
                        )
                    }
                    else -> {
                        ev.validateRequiredParams("citizen", "navigator", "amount")
                        buildEvent(
                            ev,
                            amount = BigDecimal.ZERO,
                            delta = ev.requireBigDecimalParam("amount").negate(),
                        )
                    }
                }
            }

    private fun buildEvent(ev: IndexedEvent, amount: BigDecimal, delta: BigDecimal) =
        NavigatorDelegationEvent(
            id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_${ev.eventType}"),
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
