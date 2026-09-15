package org.vechain.indexer.safe

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.safe.SafeEventUtils.MEMBERSHIP_EVENTS
import org.vechain.indexer.safe.SafeEventUtils.addressParam
import org.vechain.indexer.safe.SafeEventUtils.safeOf
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock

/** One row per `(safe, owner)`, opened by `SafeSetup` or `AddedOwner`, ended by `RemovedOwner`. */
@Profile("safe")
@Service
open class SafeMembershipService(private val repository: SafeWriteRepository) {

    companion object {
        const val SAFE_SETUP = "SafeSetup"
        const val ADDED_OWNER = "AddedOwner"
        const val REMOVED_OWNER = "RemovedOwner"
    }

    /** The new row of each (safe, owner) pair touched in each block, in ascending block order. */
    open fun processEvents(
        events: List<IndexedEvent>,
        knownSafes: Set<String>,
    ): List<SafeMembership> {
        val relevant = events.filter {
            it.eventType in MEMBERSHIP_EVENTS && safeOf(it) in knownSafes
        }
        if (relevant.isEmpty()) return emptyList()

        val keys =
            relevant
                .flatMap { event ->
                    val safe = safeOf(event) ?: return@flatMap emptyList()
                    owners(event).map { safe to it }
                }
                .toSet()
        // Carries each membership across the entry's blocks, so a later block builds on it.
        val current =
            repository
                .findCurrentMemberships(keys)
                .associateBy { it.safe to it.owner }
                .toMutableMap()
        val rows = linkedMapOf<Triple<Long, String, String>, SafeMembership>()

        groupByBlock(relevant).forEach { (blockDetails, blockEvents) ->
            blockEvents.forEach { event ->
                val safe = safeOf(event) ?: return@forEach
                owners(event).forEach { owner ->
                    val updated = apply(event, safe, owner, current[safe to owner], blockDetails)
                    current[safe to owner] = updated
                    rows[Triple(blockDetails.blockNumber, safe, owner)] = updated
                }
            }
        }
        return rows.values.toList()
    }

    private fun apply(
        event: IndexedEvent,
        safe: String,
        owner: String,
        existing: SafeMembership?,
        block: BlockDetails,
    ): SafeMembership =
        when (event.eventType) {
            REMOVED_OWNER ->
                membership(safe, owner, existing, block)
                    .copy(removedBlock = block.blockNumber, removedTimestamp = block.blockTimestamp)
            // A re-add restarts the ownership from this block.
            else ->
                membership(safe, owner, existing, block)
                    .copy(
                        addedBlock = block.blockNumber,
                        addedTimestamp = block.blockTimestamp,
                        removedBlock = null,
                        removedTimestamp = null,
                    )
        }

    private fun membership(
        safe: String,
        owner: String,
        existing: SafeMembership?,
        block: BlockDetails,
    ): SafeMembership =
        existing?.copy(
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
        )
            ?: SafeMembership(
                id = SafeMembership.buildId(safe, owner),
                safe = safe,
                owner = owner,
                addedBlock = block.blockNumber,
                addedTimestamp = block.blockTimestamp,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
            )

    /** The owner addresses the event carries: a list on setup, a single one otherwise. */
    private fun owners(event: IndexedEvent): List<String> =
        when (event.eventType) {
            SAFE_SETUP -> {
                @Suppress("UNCHECKED_CAST")
                (event.params.params["owners"] as? List<String>)
                    ?.filter { it.isNotBlank() }
                    ?.map { HexUtils.normalise(it) } ?: emptyList()
            }
            else -> listOfNotNull(addressParam(event, "owner"))
        }
}
