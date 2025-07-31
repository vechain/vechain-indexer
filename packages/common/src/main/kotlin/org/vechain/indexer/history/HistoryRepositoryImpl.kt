package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BlacklistableRepository

@Profile("history")
@Component
open class HistoryRepositoryImpl(mongoTemplate: MongoTemplate) :
    BlacklistableRepository<IndexedHistoryEvent>(mongoTemplate, IndexedHistoryEvent::class.java)
