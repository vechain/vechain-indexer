package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorCitizenService(
    private val repository: NavigatorCitizenRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    open fun findByAddress(address: String): NavigatorCitizen? = repository.findByIdOrNull(address)

    open fun processBlockEvents(
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorCitizen>,
    ) {
        for (ev in events) {
            when (ev.eventType) {
                "B3TR_DelegationCreated" -> handleCreated(ev, blockDetails, accumulator)
                "B3TR_DelegationIncreased" -> handleIncreased(ev, blockDetails, accumulator)
                "B3TR_DelegationDecreased" -> handleDecreased(ev, blockDetails, accumulator)
                "B3TR_DelegationRemoved" -> handleRemoved(ev, blockDetails, accumulator)
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<NavigatorCitizen>, existing: List<NavigatorCitizen>) {
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    private fun handleCreated(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorCitizen>,
    ) {
        val citizen = ev.params.getAsString("citizen")?.lowercase() ?: return
        val navigator = ev.params.getAsString("navigator")?.lowercase() ?: return
        val (existing, nextVersion) = accumulator.resolve(citizen)
        val created =
            NavigatorCitizen(
                address = citizen,
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                navigator = navigator,
                amount = ev.params.getAsString("amount")?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                delegatedAt = block.blockTimestamp,
                active = true,
            )
        accumulator.put(citizen, existing, created)
    }

    private fun handleIncreased(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorCitizen>,
    ) {
        val citizen = ev.params.getAsString("citizen")?.lowercase() ?: return
        val (existing, nextVersion) = accumulator.resolve(citizen)
        val current = existing ?: return
        val newTotal = ev.params.getAsString("newTotal")?.toBigDecimalOrNull() ?: current.amount
        val updated =
            current.copy(
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                amount = newTotal,
            )
        accumulator.put(citizen, existing, updated)
    }

    private fun handleDecreased(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorCitizen>,
    ) {
        val citizen = ev.params.getAsString("citizen")?.lowercase() ?: return
        val (existing, nextVersion) = accumulator.resolve(citizen)
        val current = existing ?: return
        val newTotal = ev.params.getAsString("newTotal")?.toBigDecimalOrNull() ?: current.amount
        val updated =
            current.copy(
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                amount = newTotal,
            )
        accumulator.put(citizen, existing, updated)
    }

    private fun handleRemoved(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorCitizen>,
    ) {
        val citizen = ev.params.getAsString("citizen")?.lowercase() ?: return
        val navigator = ev.params.getAsString("navigator")?.lowercase() ?: return
        val (existing, nextVersion) = accumulator.resolve(citizen)
        val current = existing ?: return
        // Skip if citizen already re-delegated to a different navigator (e.g. auto-clear
        // DelegationRemoved for old navigator arrived after DelegationCreated for new one)
        if (current.navigator != navigator) return
        val updated =
            current.copy(
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                active = false,
            )
        accumulator.put(citizen, existing, updated)
    }
}
