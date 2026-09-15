package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.BlockDetails

/** The navigator and citizen rows one block adds: exits fallen due first, then its events. */
@Profile("b3tr", "b3tr-navigator")
@Service
open class NavigatorService(private val repository: NavigatorWriteRepository) {

    /** What the schema holds for the keys the block touches, then what the block makes of them. */
    private class Ledger(navigators: List<Navigator>, citizens: List<NavigatorCitizen>) {
        val navigators = navigators.associateBy { it.address }.toMutableMap()
        val citizens = citizens.associateBy { it.address }.toMutableMap()
        val navigatorRows = linkedMapOf<String, Navigator>()
        val citizenRows = linkedMapOf<String, NavigatorCitizen>()

        fun put(navigator: Navigator) {
            navigators[navigator.address] = navigator
            navigatorRows[navigator.address] = navigator
        }

        fun put(citizen: NavigatorCitizen) {
            citizens[citizen.address] = citizen
            citizenRows[citizen.address] = citizen
        }
    }

    open fun processBlock(block: BlockDetails, events: List<IndexedEvent>): NavigatorUpdate {
        val relevant = events.filter { it.eventType in EVENTS }
        val expired = repository.findExpiredExits(block.blockNumber)
        if (relevant.isEmpty() && expired.isEmpty()) return NavigatorUpdate()

        val ledger = load(relevant, expired)
        expired.forEach { deactivate(ledger.navigators.getValue(it.address), block, ledger) }
        relevant.forEach { apply(it, block, ledger) }
        return NavigatorUpdate(
            navigators = ledger.navigatorRows.values.toList(),
            citizens = ledger.citizenRows.values.toList(),
        )
    }

    // A navigator that ends this block takes its active citizens with it, so they load too.
    private fun load(events: List<IndexedEvent>, expired: List<Navigator>): Ledger {
        val navigators = events.map { it.requireAddressParam("navigator") }.toSet()
        val ending =
            expired.map { it.address }.toSet() +
                events
                    .filter { it.eventType == DEACTIVATED }
                    .map {
                        it.requireAddressParam("navigator")
                    }
        val citizens =
            events
                .filter { it.eventType in DELEGATION_EVENTS }
                .map { it.requireAddressParam("citizen") }
                .toSet()
        val loaded = expired.map { it.address }.toSet()
        return Ledger(
            navigators = expired + repository.findCurrentNavigators(navigators - loaded),
            citizens =
                repository.findActiveCitizens(ending) + repository.findCurrentCitizens(citizens),
        )
    }

    private fun apply(ev: IndexedEvent, block: BlockDetails, ledger: Ledger) {
        val address = ev.requireAddressParam("navigator")
        when (ev.eventType) {
            REGISTERED -> {
                ev.validateRequiredParams("stakeAmount", "metadataURI")
                ledger.put(
                    Navigator(
                        address = address,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        status = NavigatorStatus.ACTIVE,
                        stake = ev.requireBigDecimalParam("stakeAmount"),
                        citizenCount = 0,
                        totalDelegated = BigDecimal.ZERO,
                        metadataURI = ev.requireParam("metadataURI"),
                        registeredAt = block.blockTimestamp,
                        exitAnnouncedRound = null,
                        exitEffectiveDeadlineBlock = null,
                        lastReportRound = null,
                        lastReportURI = null,
                    )
                )
            }
            STAKE_ADDED ->
                update(ev, block, ledger, "amount", "newTotal") {
                    it.copy(stake = ev.requireBigDecimalParam("newTotal"))
                }
            STAKE_WITHDRAWN ->
                update(ev, block, ledger, "amount", "remaining") {
                    it.copy(stake = ev.requireBigDecimalParam("remaining"))
                }
            EXIT_ANNOUNCED ->
                update(ev, block, ledger, "announcedAtRound", "effectiveDeadline") {
                    it.copy(
                        status = NavigatorStatus.EXITING,
                        exitAnnouncedRound = ev.requireLongParam("announcedAtRound"),
                        exitEffectiveDeadlineBlock = ev.requireLongParam("effectiveDeadline"),
                    )
                }
            DEACTIVATED -> {
                ev.validateRequiredParams("slashPercentage")
                ledger.navigators[address]?.let { deactivate(it, block, ledger) }
            }
            SLASHED ->
                update(ev, block, ledger, "amount", "remainingStake", "reason") {
                    it.copy(stake = ev.requireBigDecimalParam("remainingStake"))
                }
            MINOR_SLASHED ->
                update(
                    ev,
                    block,
                    ledger,
                    "amount",
                    "remainingStake",
                    "roundId",
                    "infractionFlags",
                ) {
                    it.copy(stake = ev.requireBigDecimalParam("remainingStake"))
                }
            METADATA_UPDATED ->
                update(ev, block, ledger, "newURI") {
                    it.copy(metadataURI = ev.requireParam("newURI"))
                }
            REPORT_SUBMITTED ->
                update(ev, block, ledger, "roundId", "reportURI") {
                    it.copy(
                        lastReportRound = ev.requireLongParam("roundId"),
                        lastReportURI = ev.requireParam("reportURI"),
                    )
                }
            in DELEGATION_EVENTS -> delegation(ev, block, ledger)
        }
    }

    private fun delegation(ev: IndexedEvent, block: BlockDetails, ledger: Ledger) {
        val navigator = ev.requireAddressParam("navigator")
        val citizen = ev.requireAddressParam("citizen")
        val current = ledger.citizens[citizen]
        when (ev.eventType) {
            DELEGATION_CREATED -> {
                val amount = ev.requireBigDecimalParam("amount")
                update(ev, block, ledger) {
                    it.copy(
                        citizenCount = it.citizenCount + 1,
                        totalDelegated = it.totalDelegated + amount,
                    )
                }
                ledger.put(
                    NavigatorCitizen(
                        address = citizen,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        navigator = navigator,
                        amount = amount,
                        delegatedAt = block.blockTimestamp,
                        active = true,
                    )
                )
            }
            DELEGATION_INCREASED -> {
                val added = ev.requireBigDecimalParam("addedAmount")
                val newTotal = ev.requireBigDecimalParam("newTotal")
                update(ev, block, ledger) { it.copy(totalDelegated = it.totalDelegated + added) }
                current?.let { ledger.put(it.at(block).copy(amount = newTotal)) }
            }
            DELEGATION_DECREASED -> {
                val removed = ev.requireBigDecimalParam("removedAmount")
                val newTotal = ev.requireBigDecimalParam("newTotal")
                update(ev, block, ledger) {
                    it.copy(totalDelegated = (it.totalDelegated - removed).max(BigDecimal.ZERO))
                }
                current?.let { ledger.put(it.at(block).copy(amount = newTotal)) }
            }
            DELEGATION_REMOVED -> {
                val amount = ev.requireBigDecimalParam("amount")
                update(ev, block, ledger) {
                    it.copy(
                        citizenCount = maxOf(0, it.citizenCount - 1),
                        totalDelegated = (it.totalDelegated - amount).max(BigDecimal.ZERO),
                    )
                }
                // A removal for the old navigator can trail the citizen's re-delegation to a new
                // one.
                if (current != null && current.navigator == navigator) {
                    ledger.put(current.at(block).copy(active = false))
                }
            }
        }
    }

    /** Applies [transform] to the event's navigator, if the schema knows it. */
    private fun update(
        ev: IndexedEvent,
        block: BlockDetails,
        ledger: Ledger,
        vararg params: String,
        transform: (Navigator) -> Navigator,
    ) {
        ev.validateRequiredParams(*params)
        val current = ledger.navigators[ev.requireAddressParam("navigator")] ?: return
        ledger.put(transform(current.at(block)))
    }

    private fun deactivate(navigator: Navigator, block: BlockDetails, ledger: Ledger) {
        ledger.put(
            navigator
                .at(block)
                .copy(
                    status = NavigatorStatus.DEACTIVATED,
                    citizenCount = 0,
                    totalDelegated = BigDecimal.ZERO,
                )
        )
        ledger.citizens.values
            .filter { it.active && it.navigator == navigator.address }
            .forEach { ledger.put(it.at(block).copy(active = false)) }
    }

    private fun Navigator.at(block: BlockDetails) =
        copy(
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
        )

    private fun NavigatorCitizen.at(block: BlockDetails) =
        copy(
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
        )

    companion object {
        const val REGISTERED = "B3TR_NavigatorRegistered"
        const val STAKE_ADDED = "B3TR_StakeAdded"
        const val STAKE_WITHDRAWN = "B3TR_StakeWithdrawn"
        const val EXIT_ANNOUNCED = "B3TR_ExitAnnounced"
        const val DEACTIVATED = "B3TR_NavigatorDeactivated"
        const val SLASHED = "B3TR_NavigatorSlashed"
        const val MINOR_SLASHED = "B3TR_NavigatorMinorSlashed"
        const val METADATA_UPDATED = "B3TR_MetadataURIUpdated"
        const val REPORT_SUBMITTED = "B3TR_ReportSubmitted"
        const val DELEGATION_CREATED = "B3TR_DelegationCreated"
        const val DELEGATION_INCREASED = "B3TR_DelegationIncreased"
        const val DELEGATION_DECREASED = "B3TR_DelegationDecreased"
        const val DELEGATION_REMOVED = "B3TR_DelegationRemoved"

        val DELEGATION_EVENTS =
            listOf(
                DELEGATION_CREATED,
                DELEGATION_INCREASED,
                DELEGATION_DECREASED,
                DELEGATION_REMOVED,
            )

        val EVENTS =
            listOf(
                REGISTERED,
                STAKE_ADDED,
                STAKE_WITHDRAWN,
                EXIT_ANNOUNCED,
                DEACTIVATED,
                SLASHED,
                MINOR_SLASHED,
                METADATA_UPDATED,
                REPORT_SUBMITTED,
            ) + DELEGATION_EVENTS
    }
}
