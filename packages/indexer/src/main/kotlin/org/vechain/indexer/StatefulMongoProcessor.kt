package org.vechain.indexer

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

abstract class StatefulMongoProcessor(
    repository: BaseIndexedRepository<*, *>,
    mongoTemplate: MongoTemplate,
    indexerName: String,
    checkpointService: CheckpointService,
    collectionName: String,
    processorMetrics: ProcessorMetrics,
) :
    MongoProcessor(
        repository,
        indexerName,
        checkpointService,
        collectionName,
        processorMetrics,
        VersionedMongoIndexerStore(
            repository,
            mongoTemplate,
            checkpointService,
            collectionName,
            indexerName,
        ),
    )
