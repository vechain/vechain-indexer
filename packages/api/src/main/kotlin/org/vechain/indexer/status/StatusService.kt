package org.vechain.indexer.status

import org.springframework.stereotype.Service
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.IndexerCheckpoint
import org.vechain.indexer.postgres.IndexerStateRepository

@Service
@ConditionalOnPostgres
open class StatusService(private val stateRepository: IndexerStateRepository) {

    open fun checkpoints(): List<IndexerCheckpoint> = stateRepository.checkpoints()
}
