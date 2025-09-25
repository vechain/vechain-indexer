package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.round.RoundUtils.discoverRoundId
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryProcessor(
    private val repository: UserRoundActionSummaryRepository,
    userRoundActionSummaryArchiveService:
        ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
    private val service: UserRoundActionSummaryService,
    @param:Value("\${indexer.start-round.b3tr-sustainable-actions}") private val startRound: Int,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = userRoundActionSummaryArchiveService,
    ) {

    protected var roundId: Int =
        repository.findFirstByOrderByBlockNumberDesc()?.roundId ?: startRound

    @WithTiming("UserRoundActionSummaryProcessor.process")
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
            val currRoundId = discoverRoundId(roundChangeEvents, roundId)
            roundId = currRoundId

            if (rewardDistributedEvents.isEmpty()) {
                // No relevant events to process in this block
                return@forEach
            }

            val (updated, archives) =
                service.processEvents(blockDetails, rewardDistributedEvents, currRoundId)

            // Save the updated NFTs and archives
            service.save(updated, archives)
        }
    }
}
