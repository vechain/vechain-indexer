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
open class NavigatorFeeSummaryService(
    private val repository: NavigatorFeeSummaryRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    open fun findById(id: String): NavigatorFeeSummaryDocument? = repository.findByIdOrNull(id)

    open fun processBlockEvents(
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorFeeSummaryDocument>,
    ) {
        events.forEach { event ->
            when (event.eventType) {
                "B3TR_FeeDeposited" -> handleDeposited(event, blockDetails, accumulator)
                "B3TR_FeeClaimed" -> handleClaimed(event, blockDetails, accumulator)
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(
        updated: List<NavigatorFeeSummaryDocument>,
        existing: List<NavigatorFeeSummaryDocument>,
    ) {
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    private fun handleDeposited(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorFeeSummaryDocument>,
    ) {
        val navigator = event.params.getAsString("navigator")?.lowercase() ?: return
        val amount = event.params.getAsString("amount")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        updateSummary(
            NavigatorFeeSummaryDocument.GLOBAL_ID,
            null,
            NavigatorFeeSummaryRecordType.GLOBAL_SUMMARY,
            block,
            accumulator,
        ) { current ->
            current.copy(totalEarned = current.totalEarned + amount)
        }
        updateSummary(
            NavigatorFeeSummaryDocument.navigatorSummaryId(navigator),
            navigator,
            NavigatorFeeSummaryRecordType.NAVIGATOR_SUMMARY,
            block,
            accumulator,
        ) { current ->
            current.copy(totalEarned = current.totalEarned + amount)
        }
    }

    private fun handleClaimed(
        event: IndexedEvent,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorFeeSummaryDocument>,
    ) {
        val navigator = event.params.getAsString("navigator")?.lowercase() ?: return
        val amount = event.params.getAsString("amount")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        updateSummary(
            NavigatorFeeSummaryDocument.GLOBAL_ID,
            null,
            NavigatorFeeSummaryRecordType.GLOBAL_SUMMARY,
            block,
            accumulator,
        ) { current ->
            current.copy(totalClaimed = current.totalClaimed + amount)
        }
        updateSummary(
            NavigatorFeeSummaryDocument.navigatorSummaryId(navigator),
            navigator,
            NavigatorFeeSummaryRecordType.NAVIGATOR_SUMMARY,
            block,
            accumulator,
        ) { current ->
            current.copy(totalClaimed = current.totalClaimed + amount)
        }
    }

    private fun updateSummary(
        id: String,
        navigator: String?,
        recordType: NavigatorFeeSummaryRecordType,
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NavigatorFeeSummaryDocument>,
        transform: (NavigatorFeeSummaryDocument) -> NavigatorFeeSummaryDocument,
    ) {
        val (existing, nextVersion) = accumulator.resolve(id)
        val current =
            existing
                ?: repository.findByIdOrNull(id)
                ?: NavigatorFeeSummaryDocument(
                    id = id,
                    version = 0,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    recordType = recordType,
                    navigator = navigator,
                    totalEarned = BigDecimal.ZERO,
                    totalClaimed = BigDecimal.ZERO,
                )
        val updated =
            transform(current)
                .copy(
                    version = nextVersion,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                    recordType = recordType,
                    navigator = navigator,
                )
        accumulator.put(id, existing ?: repository.findByIdOrNull(id), updated)
    }
}
