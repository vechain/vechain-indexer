package org.vechain.indexer.amn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.thor.model.BlockRevision

@Profile("authority-nodes")
@Component
open class AmnProcessor(
    private val repository: AmnRepository,
    private val amnService: AmnService,
    private val thorClient: ThorClient,
    checkpointService: CheckpointService,
) :
    BaseProcessor(
        repository,
        IndexerNames.AUTHORITY_NODE,
        checkpointService = checkpointService,
        collectionName = "authority_nodes",
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private var hasSynced = false

    override suspend fun processEntry(entry: IndexingResult) {
        if (!hasSynced && withContext(Dispatchers.IO) { repository.count() } == 0L) {
            logger.info("No Authority Nodes found – syncing after collection setup...")
            amnService.syncEndorsersForAllNodes()
            logger.info("Initial Authority Node sync complete.")

            hasSynced = true
        }

        val toSave = amnService.processCandidateEvents(entry.events())

        if (toSave.isNotEmpty()) {
            withContext(Dispatchers.IO) { amnService.save(toSave) }
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        if (!hasSynced && repository.count() == 0L) {
            val finalizedBlock = runBlocking {
                thorClient.getBlockUnexpanded(BlockRevision.Keyword.FINALIZED)
            }
            return BlockIdentifier(id = finalizedBlock.id, number = finalizedBlock.number)
        }
        return super.getLastSyncedBlock()
    }
}
