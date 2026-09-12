package org.vechain.indexer.blocks

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.thor.model.BlockIdentifier

/** Every block writes a row, so the newest one is the resume point and no checkpoint is kept. */
@Profile("blocks")
@Component
open class BlocksIndexerStore(
    private val repository: BlocksWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
) : PostgresIndexerStore(IndexerNames.BLOCKS.COLLECTION, repository, state, checkpointProperties) {

    override val usesCheckpoint: Boolean = false

    override fun lastSynced(): BlockIdentifier? = repository.lastSynced()
}
