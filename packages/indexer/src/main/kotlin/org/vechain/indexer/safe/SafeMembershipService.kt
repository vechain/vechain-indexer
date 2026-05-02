package org.vechain.indexer.safe

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.safe.repository.SafeMembershipRepository
import org.vechain.indexer.safe.repository.SafeProxyRepository
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * Maintains one `(safe, owner)` document per Safe membership relationship.
 *
 * Source events (all on-chain, no contract reads):
 * - `SafeSetup` seeds the initial owner set when a Safe is initialised.
 * - `AddedOwner` adds an owner (or re-adds a previously removed one).
 * - `RemovedOwner` archives the relationship by setting `removedBlock`.
 *
 * The Safe contract address is the log's `address` field; that's the safe identity.
 */
@Profile("safe")
@Service
open class SafeMembershipService(
    private val repository: SafeMembershipRepository,
    private val safeProxyRepository: SafeProxyRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    companion object {
        const val SAFE_SETUP = "SafeSetup"
        const val ADDED_OWNER = "AddedOwner"
        const val REMOVED_OWNER = "RemovedOwner"

        private val SUPPORTED_EVENTS = setOf(SAFE_SETUP, ADDED_OWNER, REMOVED_OWNER)
    }

    open fun processBlock(
        events: List<IndexedEvent>
    ): Pair<List<SafeMembership>, List<SafeMembership>> {
        val membershipEvents = events.filter { it.eventType in SUPPORTED_EVENTS }
        if (membershipEvents.isEmpty()) {
            return emptyList<SafeMembership>() to emptyList()
        }

        // Drop events from addresses that are not registered Safes. The safe-proxy indexer is the
        // trust root: an address only enters here after `SafeProxyFactory.ProxyCreation`. Topic
        // signatures alone are not enough — anyone could emit a colliding signature.
        val verifiedEvents = filterByKnownSafes(membershipEvents)
        if (verifiedEvents.isEmpty()) {
            return emptyList<SafeMembership>() to emptyList()
        }

        // Pre-collect record ids and batch load existing docs for the impacted (safe, owner) pairs.
        val candidateIds = mutableSetOf<String>()
        verifiedEvents.forEach { event ->
            val safe = event.address?.let { HexUtils.normalise(it) } ?: return@forEach
            extractOwners(event).forEach { owner ->
                candidateIds.add(SafeMembership.buildId(safe, owner))
            }
        }
        val preloaded =
            if (candidateIds.isNotEmpty()) {
                repository.findAllById(candidateIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<SafeMembership>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) },
                initialVersion = 1,
            )

        groupByBlock(verifiedEvents).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            blockEvents.forEach { event -> applyEvent(event, blockDetails, accumulator) }
        }

        return accumulator.results()
    }

    private fun filterByKnownSafes(events: List<IndexedEvent>): List<IndexedEvent> {
        val candidateSafes =
            events.mapNotNull { it.address?.let { addr -> HexUtils.normalise(addr) } }.toSet()
        if (candidateSafes.isEmpty()) return emptyList()
        val knownSafes = safeProxyRepository.findAllById(candidateSafes).map { it.id }.toSet()
        return events.filter {
            it.address?.let { addr -> HexUtils.normalise(addr) in knownSafes } == true
        }
    }

    private fun applyEvent(
        event: IndexedEvent,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<SafeMembership>,
    ) {
        val safe = event.address?.let { HexUtils.normalise(it) } ?: return
        val owners = extractOwners(event)
        if (owners.isEmpty()) return

        when (event.eventType) {
            SAFE_SETUP,
            ADDED_OWNER ->
                owners.forEach { owner -> addOrReinstate(safe, owner, blockDetails, accumulator) }
            REMOVED_OWNER ->
                owners.forEach { owner -> markRemoved(safe, owner, blockDetails, accumulator) }
        }
    }

    private fun addOrReinstate(
        safe: String,
        owner: String,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<SafeMembership>,
    ) {
        val recordId = SafeMembership.buildId(safe, owner)
        val (existing, nextVersion) = accumulator.resolve(recordId)
        val updated =
            if (existing == null) {
                SafeMembership(
                    id = recordId,
                    safe = safe,
                    owner = owner,
                    addedBlock = blockDetails.blockNumber,
                    addedTimestamp = blockDetails.blockTimestamp,
                    removedBlock = null,
                    removedTimestamp = null,
                    blockId = blockDetails.blockId,
                    blockNumber = blockDetails.blockNumber,
                    blockTimestamp = blockDetails.blockTimestamp,
                    version = nextVersion,
                )
            } else {
                // Re-add: reset added* timestamps to this event and clear removed*.
                existing.copy(
                    addedBlock = blockDetails.blockNumber,
                    addedTimestamp = blockDetails.blockTimestamp,
                    removedBlock = null,
                    removedTimestamp = null,
                    blockId = blockDetails.blockId,
                    blockNumber = blockDetails.blockNumber,
                    blockTimestamp = blockDetails.blockTimestamp,
                    version = nextVersion,
                )
            }
        accumulator.put(recordId, existing, updated)
    }

    private fun markRemoved(
        safe: String,
        owner: String,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<SafeMembership>,
    ) {
        val recordId = SafeMembership.buildId(safe, owner)
        val (existing, nextVersion) = accumulator.resolve(recordId)
        // We never observed an Add for this owner; record the removal anyway so the doc shape
        // remains consistent. addedBlock/Timestamp default to this block.
        val updated =
            existing?.copy(
                removedBlock = blockDetails.blockNumber,
                removedTimestamp = blockDetails.blockTimestamp,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                version = nextVersion,
            )
                ?: SafeMembership(
                    id = recordId,
                    safe = safe,
                    owner = owner,
                    addedBlock = blockDetails.blockNumber,
                    addedTimestamp = blockDetails.blockTimestamp,
                    removedBlock = blockDetails.blockNumber,
                    removedTimestamp = blockDetails.blockTimestamp,
                    blockId = blockDetails.blockId,
                    blockNumber = blockDetails.blockNumber,
                    blockTimestamp = blockDetails.blockTimestamp,
                    version = nextVersion,
                )
        accumulator.put(recordId, existing, updated)
    }

    /** Pulls the owner addresses out of the event payload, depending on the event type. */
    private fun extractOwners(event: IndexedEvent): List<String> =
        when (event.eventType) {
            SAFE_SETUP -> {
                @Suppress("UNCHECKED_CAST")
                (event.params.params["owners"] as? List<String>)
                    ?.filter { it.isNotBlank() }
                    ?.map { HexUtils.normalise(it) } ?: emptyList()
            }
            ADDED_OWNER,
            REMOVED_OWNER ->
                listOfNotNull(
                    event.params
                        .getAsString("owner")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { HexUtils.normalise(it) }
                )
            else -> emptyList()
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<SafeMembership>, existing: List<SafeMembership>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
            minVersions = inlineVersioningProperties.minVersions,
        )
    }
}
