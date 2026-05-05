package org.vechain.indexer.b3tr.relayer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.relayer.repository.AutoVotingToggleRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * Persists at most one row per `(address, activeFromRound)`. Within a slice (single source round),
 * if an address toggles multiple times, only the last on-chain event survives — that's what the
 * contract checkpoint will reflect at the next round's snapshot.
 *
 * The processor invokes us once per round-slice with `roundId = source round of the events`. Rows
 * are written with `activeFromRound = roundId + 1`, encoding the "effective from next round" rule
 * directly in the data.
 */
@Profile("b3tr", "b3tr-auto-voting-toggles")
@Service
open class AutoVotingToggleService(
    private val repository: AutoVotingToggleRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    open fun processEvents(
        events: List<IndexedEvent>,
        roundId: Int,
    ): Pair<List<AutoVotingToggle>, List<AutoVotingToggle>> {
        val toggles = events.filter { it.eventType == AUTO_VOTING_TOGGLED_EVENT }
        if (toggles.isEmpty()) return emptyList<AutoVotingToggle>() to emptyList()

        val activeFromRound = roundId + 1

        // Preload any existing rows we may update so the accumulator can carry forward versions.
        val recordIds =
            toggles
                .mapTo(mutableSetOf()) { event -> generateId(addressOf(event), "$activeFromRound") }
                .toList()
        val preloaded =
            if (recordIds.isNotEmpty()) {
                repository.findAllById(recordIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<AutoVotingToggle>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) }
            )

        groupByBlock(toggles).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()

            // Within a single block, dedupe per address — last event wins (chain order).
            val perAddress = LinkedHashMap<String, IndexedEvent>()
            for (event in blockEvents) {
                perAddress[addressOf(event)] = event
            }

            perAddress.forEach { (address, event) ->
                val recordId = generateId(address, "$activeFromRound")
                val (existing, nextVersion) = accumulator.resolve(recordId)
                val updated =
                    AutoVotingToggle(
                        id = recordId,
                        address = address,
                        enabled = enabledOf(event),
                        activeFromRound = activeFromRound,
                        blockId = blockDetails.blockId,
                        blockNumber = blockDetails.blockNumber,
                        blockTimestamp = blockDetails.blockTimestamp,
                        version = nextVersion,
                    )
                accumulator.put(recordId, existing, updated)
            }
        }

        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<AutoVotingToggle>, existing: List<AutoVotingToggle>) {
        if (updated.isEmpty() && existing.isEmpty()) return
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
            inlineVersioningProperties.minVersions,
        )
    }

    private fun addressOf(event: IndexedEvent): String =
        (event.params.getAsString("account")
                ?: error("AutoVotingToggled missing 'account' (event ${event.id})"))
            .lowercase()

    private fun enabledOf(event: IndexedEvent): Boolean =
        event.params.getAsBoolean("enabled")
            ?: error("AutoVotingToggled missing 'enabled' (event ${event.id})")

    companion object {
        private const val AUTO_VOTING_TOGGLED_EVENT = "AutoVotingToggled"
    }
}
