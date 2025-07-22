package org.vechain.indexer.amn

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.repository.AmnRepository
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("authority-nodes")
@Component
open class AmnProcessor(
    private val repository: AmnRepository,
    private val amnService: AmnService,
    private val thorService: ThorService,
) : BaseProcessor(repository) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private var hasSynced = false

    override fun process(events: List<IndexedEvent>, block: Block?) {
        if (!hasSynced && repository.count() == 0L) {
            logger.info("No Authority Nodes found – syncing after collection setup...")
            amnService.syncEndorsersForAllNodes()
            logger.info("Initial Authority Node sync complete.")

            hasSynced = true
        }

        amnService.processCandidateEvents(events)
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        if (!hasSynced && repository.count() == 0L) {
            val bestBlock = thorService.getBestBlock()
            return BlockIdentifier(id = bestBlock.id, number = bestBlock.number)
        }
        return super.getLastSyncedBlock()
    }
}
