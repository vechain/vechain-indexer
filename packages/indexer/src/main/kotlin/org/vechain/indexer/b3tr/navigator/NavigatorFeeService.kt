package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.BlockDetails

/** One row per (navigator, round): deposits accumulate into it, a claim marks it paid out. */
@Profile("b3tr", "b3tr-navigator")
@Service
open class NavigatorFeeService(private val repository: NavigatorWriteRepository) {

    open fun processBlock(block: BlockDetails, events: List<IndexedEvent>): List<NavigatorFee> {
        val relevant = events.filter { it.eventType in EVENTS }
        if (relevant.isEmpty()) return emptyList()
        relevant.forEach { it.validateRequiredParams("navigator", "roundId", "amount") }

        val current =
            repository
                .findCurrentFees(relevant.map { key(it) }.toSet(), block.blockNumber)
                .associateBy { it.navigator to it.roundId }
                .toMutableMap()
        val rows = linkedMapOf<Pair<String, Int>, NavigatorFee>()
        relevant.forEach { ev ->
            val key = key(ev)
            val amount = ev.requireBigDecimalParam("amount")
            val existing = current[key]
            val updated =
                if (ev.eventType == FEE_DEPOSITED) {
                    existing?.at(block)?.copy(totalDeposited = existing.totalDeposited + amount)
                        ?: NavigatorFee(
                            navigator = key.first,
                            roundId = key.second,
                            blockId = block.blockId,
                            blockNumber = block.blockNumber,
                            blockTimestamp = block.blockTimestamp,
                            totalDeposited = amount,
                            claimedAmount = null,
                            claimedAt = null,
                            depositedAt = block.blockTimestamp,
                            unlockRound = key.second + NavigatorFee.FEE_LOCK_PERIOD,
                        )
                } else {
                    checkNotNull(existing) {
                        "Missing navigator fee deposit for claimed fee navigator=${key.first} " +
                            "roundId=${key.second}"
                    }
                    existing
                        .at(block)
                        .copy(
                            claimedAmount = (existing.claimedAmount ?: BigDecimal.ZERO) + amount,
                            claimedAt = block.blockTimestamp,
                        )
                }
            current[key] = updated
            rows[key] = updated
        }
        return rows.values.toList()
    }

    private fun key(ev: IndexedEvent) =
        ev.requireAddressParam("navigator") to ev.requireIntParam("roundId")

    private fun NavigatorFee.at(block: BlockDetails) =
        copy(
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
        )

    companion object {
        const val FEE_DEPOSITED = "B3TR_FeeDeposited"
        const val FEE_CLAIMED = "B3TR_FeeClaimed"
        val EVENTS = listOf(FEE_DEPOSITED, FEE_CLAIMED)
    }
}
