package org.vechain.indexer.b3tr.navigator

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

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorFeeService(
    private val repository: NavigatorFeeRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {

    open fun findById(id: String): NavigatorFee? = repository.findByIdOrNull(id)

    open fun processBlockEvents(
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorFee>,
    ) {
        for (ev in events) {
            when (ev.eventType) {
                "B3TR_FeeDeposited" -> handleDeposited(ev, blockDetails, accumulator)
                "B3TR_FeeClaimed" -> handleClaimed(ev, blockDetails, accumulator)
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<NavigatorFee>, existing: List<NavigatorFee>) {
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    private fun handleDeposited(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorFee>,
    ) {
        ev.validateRequiredParams("navigator", "roundId", "amount")
        val navigator = ev.requireAddressParam("navigator")
        val roundId = ev.requireIntParam("roundId")
        val amount = ev.requireBigDecimalParam("amount")
        val id = NavigatorFee.buildId(navigator, roundId)
        val (existing, nextVersion) = accumulator.resolve(id)

        if (existing != null) {
            val updated =
                existing.copy(
                    version = nextVersion,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    totalDeposited = existing.totalDeposited + amount,
                )
            accumulator.put(id, existing, updated)
        } else {
            val created =
                NavigatorFee(
                    id = id,
                    version = nextVersion,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    navigator = navigator,
                    roundId = roundId,
                    totalDeposited = amount,
                    claimed = false,
                    claimedAt = null,
                    depositedAt = block.blockTimestamp,
                    unlockRound = roundId.toLong() + NavigatorFee.FEE_LOCK_PERIOD,
                )
            accumulator.put(id, existing, created)
        }
    }

    private fun handleClaimed(
        ev: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorFee>,
    ) {
        ev.validateRequiredParams("navigator", "roundId", "amount")
        val navigator = ev.requireAddressParam("navigator")
        val roundId = ev.requireIntParam("roundId")
        val id = NavigatorFee.buildId(navigator, roundId)
        val (existing, nextVersion) = accumulator.resolve(id)
        val current = existing ?: return
        val updated =
            current.copy(
                version = nextVersion,
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                claimed = true,
                claimedAt = block.blockTimestamp,
            )
        accumulator.put(id, existing, updated)
    }
}
