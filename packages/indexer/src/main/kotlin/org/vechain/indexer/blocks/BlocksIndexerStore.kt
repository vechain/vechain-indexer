package org.vechain.indexer.blocks

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.thor.model.BlockIdentifier

/** Every block writes a row, so the newest one is the resume point rather than the checkpoint. */
@Profile("blocks")
@Component
open class BlocksIndexerStore(
    private val repository: BlocksWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
) : PostgresIndexerStore(IndexerNames.BLOCKS.COLLECTION, repository, state, checkpointProperties) {

    // The checkpoint is still written, unused here, because /api/v1/status reports it.
    override fun lastSynced(): BlockIdentifier? = repository.lastSynced()
}
