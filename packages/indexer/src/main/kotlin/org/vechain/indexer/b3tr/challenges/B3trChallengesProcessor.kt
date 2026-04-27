package org.vechain.indexer.b3tr.challenges

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("b3tr", "b3tr-challenges")
@Component
open class B3trChallengesProcessor(
    repository: B3trChallengeRepository,
    mongoTemplate: MongoTemplate,
    private val service: B3trChallengesService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.B3TR_CHALLENGES.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.B3TR_CHALLENGES.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        val (updated, existing) = service.processEvents(entry.events())
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.save(updated, existing)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        service.invalidateRuntimeState()
        super.rollback(blockNumber)
    }
}
