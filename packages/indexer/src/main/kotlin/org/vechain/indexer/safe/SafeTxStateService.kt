package org.vechain.indexer.safe

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.safe.repository.SafeProxyRepository
import org.vechain.indexer.safe.repository.SafeTxStateRepository
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * Aggregates Safe transaction state per `(safe, txHash)`. Source events: `ApproveHash`,
 * `ExecutionSuccess`, `ExecutionFailure` — all emitted by the Safe contract itself, so the log's
 * `address` is the Safe identity.
 */
@Profile("safe")
@Service
open class SafeTxStateService(
    private val repository: SafeTxStateRepository,
    private val safeProxyRepository: SafeProxyRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    companion object {
        const val APPROVE_HASH = "ApproveHash"
        const val EXECUTION_SUCCESS = "ExecutionSuccess"
        const val EXECUTION_FAILURE = "ExecutionFailure"

        private val SUPPORTED_EVENTS = setOf(APPROVE_HASH, EXECUTION_SUCCESS, EXECUTION_FAILURE)
    }

    open fun processBlock(events: List<IndexedEvent>): Pair<List<SafeTxState>, List<SafeTxState>> {
        val safeEvents = events.filter { it.eventType in SUPPORTED_EVENTS }
        if (safeEvents.isEmpty()) {
            return emptyList<SafeTxState>() to emptyList()
        }

        // Drop events from addresses that are not registered Safes (see safe-proxy indexer).
        val verifiedEvents = filterByKnownSafes(safeEvents)
        if (verifiedEvents.isEmpty()) {
            return emptyList<SafeTxState>() to emptyList()
        }

        // Pre-collect all candidate ids for batch loading.
        val candidateIds = mutableSetOf<String>()
        verifiedEvents.forEach { event ->
            val safe = event.address?.let { HexUtils.normalise(it) } ?: return@forEach
            val txHash = extractTxHash(event) ?: return@forEach
            candidateIds.add(SafeTxState.buildId(safe, txHash))
        }
        val preloaded =
            if (candidateIds.isNotEmpty()) {
                repository.findAllById(candidateIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<SafeTxState>(
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
        accumulator: VersionedDocumentAccumulator<SafeTxState>,
    ) {
        val safe = event.address?.let { HexUtils.normalise(it) } ?: return
        val txHash = extractTxHash(event) ?: return
        val recordId = SafeTxState.buildId(safe, txHash)
        val (existing, nextVersion) = accumulator.resolve(recordId)
        val base =
            existing?.copy(
                approvers = existing.approvers.toMutableList(),
                version = nextVersion,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
            )
                ?: SafeTxState(
                    id = recordId,
                    safe = safe,
                    txHash = txHash,
                    approvers = mutableListOf(),
                    blockId = blockDetails.blockId,
                    blockNumber = blockDetails.blockNumber,
                    blockTimestamp = blockDetails.blockTimestamp,
                    version = nextVersion,
                )

        when (event.eventType) {
            APPROVE_HASH -> {
                val owner =
                    event.params.getAsString("owner")?.let { HexUtils.normalise(it) } ?: return
                if (base.approvers.any { it.owner == owner }) return
                base.approvers.add(
                    SafeTxApproval(
                        owner = owner,
                        block = blockDetails.blockNumber,
                        blockTimestamp = blockDetails.blockTimestamp,
                        vechainTxId = event.txId,
                    )
                )
            }
            EXECUTION_SUCCESS -> {
                base.executed = true
                base.failed = false
                base.executedBlock = blockDetails.blockNumber
                base.executedTimestamp = blockDetails.blockTimestamp
                base.executor = event.origin?.let { HexUtils.normalise(it) }
                base.vechainTxId = event.txId
            }
            EXECUTION_FAILURE -> {
                base.executed = true
                base.failed = true
                base.executedBlock = blockDetails.blockNumber
                base.executedTimestamp = blockDetails.blockTimestamp
                base.executor = event.origin?.let { HexUtils.normalise(it) }
                base.vechainTxId = event.txId
            }
        }
        accumulator.put(recordId, existing, base)
    }

    private fun extractTxHash(event: IndexedEvent): String? =
        when (event.eventType) {
            APPROVE_HASH -> event.params.getAsString("approvedHash")?.let { HexUtils.normalise(it) }
            EXECUTION_SUCCESS,
            EXECUTION_FAILURE -> event.params.getAsString("txHash")?.let { HexUtils.normalise(it) }
            else -> null
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<SafeTxState>, existing: List<SafeTxState>) {
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
