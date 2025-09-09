package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getCycle
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryProcessor(
    private val repository: AppRoundActionSummaryRepository,
    appRoundActionSummaryArchiveService:
        ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>,
    private val service: AppRoundActionSummaryService,
    @param:Value("\${indexer.start-round.b3tr-sustainable-actions}") private val startRound: Int,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = appRoundActionSummaryArchiveService,
    ) {

    private val logger = LoggerFactory.getLogger(AppRoundActionSummaryProcessor::class.java)

    private var roundId: Int? = null

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        // Process the events using the service
        groupByBlock(matchedEvents).forEach { (blockDetails, blockEvents) ->
            val roundChangeEvents =
                blockEvents.filter {
                    (it.eventType == "EmissionDistributed" ||
                        it.eventType == "EmissionDistributedV2")
                }
            val rewardDistributedEvents = blockEvents.filter { it.eventType == "B3TR_ActionReward" }

            // Ensure no unexpected events are present
            require(roundChangeEvents.size + rewardDistributedEvents.size == blockEvents.size) {
                "Unexpected event types found in block ${blockDetails.blockNumber}"
            }

            val (updated, archives) =
                service.processEvents(
                    blockDetails,
                    rewardDistributedEvents,
                    discoverRoundId(roundChangeEvents),
                )

            // Save the updated NFTs and archives
            service.save(updated, archives)
        }
    }

    /**
     * Discover what the current round is
     *
     * @param events used to check for a change of round id
     */
    private fun discoverRoundId(events: List<IndexedEvent>): Int {
        assertEventTypes(events, "EmissionDistributed", "EmissionDistributedV2")
        // If the roundId is not set, we need to set it
        if (roundId == null) {
            // First check to see if there is an existing record that we can use
            val existingRecord = repository.findFirstByOrderByBlockNumberDesc()
            roundId = existingRecord?.roundId ?: startRound
            logger.info("Initialising roundId: $roundId")
        }
        // Next we check if the roundId has changed in the current block
        if (events.isNotEmpty()) {
            require(events.size == 1) {
                "Expected only one EmissionDistributed or EmissionDistributedV2 event per block, but found ${events.size} in block ${events[0].blockNumber}"
            }
            val newRoundId = getCycle(events[0])
            if (newRoundId != roundId) {
                logger.info(
                    "Round has changed from $roundId to $newRoundId at block ${events[0].blockNumber}"
                )
                roundId = newRoundId
            }
        }

        return roundId ?: error("roundId should have been initialized")
    }
}
