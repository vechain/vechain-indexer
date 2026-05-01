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
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * Records every Safe proxy deployed via `SafeProxyFactory.ProxyCreation`. The proxy address comes
 * from the indexed `proxy` event param.
 */
@Profile("safe")
@Service
open class SafeProxyService(
    private val repository: SafeProxyRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    companion object {
        const val PROXY_CREATION = "ProxyCreation"
    }

    open fun processBlock(events: List<IndexedEvent>): Pair<List<SafeProxy>, List<SafeProxy>> {
        val proxyEvents = events.filter { it.eventType == PROXY_CREATION }
        if (proxyEvents.isEmpty()) {
            return emptyList<SafeProxy>() to emptyList()
        }

        val candidateIds =
            proxyEvents
                .mapNotNull { it.params.getAsString("proxy")?.let { p -> SafeProxy.buildId(p) } }
                .toSet()
        val preloaded =
            if (candidateIds.isNotEmpty()) {
                repository.findAllById(candidateIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<SafeProxy>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) },
                initialVersion = 1,
            )

        groupByBlock(proxyEvents).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            blockEvents.forEach { event -> applyEvent(event, blockDetails, accumulator) }
        }

        return accumulator.results()
    }

    private fun applyEvent(
        event: IndexedEvent,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<SafeProxy>,
    ) {
        val proxy = event.params.getAsString("proxy")?.let { HexUtils.normalise(it) } ?: return
        val singleton =
            event.params.getAsString("singleton")?.let { HexUtils.normalise(it) } ?: return
        val recordId = SafeProxy.buildId(proxy)
        val (existing, nextVersion) = accumulator.resolve(recordId)
        // ProxyCreation should fire once per Safe; if we somehow see it twice for the same address,
        // keep the original creation metadata and only bump version + bookkeeping.
        val updated =
            existing?.copy(
                version = nextVersion,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
            )
                ?: SafeProxy(
                    id = recordId,
                    singleton = singleton,
                    createdBlock = blockDetails.blockNumber,
                    createdTimestamp = blockDetails.blockTimestamp,
                    vechainTxId = event.txId,
                    blockId = blockDetails.blockId,
                    blockNumber = blockDetails.blockNumber,
                    blockTimestamp = blockDetails.blockTimestamp,
                    version = nextVersion,
                )
        accumulator.put(recordId, existing, updated)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<SafeProxy>, existing: List<SafeProxy>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
    }
}
