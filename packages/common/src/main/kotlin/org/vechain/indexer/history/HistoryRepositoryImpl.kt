package org.vechain.indexer.history

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BlacklistableRepository
import org.vechain.indexer.nft.NftBlacklistRepository

open class HistoryRepositoryImpl(mongoTemplate: MongoTemplate, repo: NftBlacklistRepository) :
    BlacklistableRepository<IndexedHistoryEvent>(
        mongoTemplate,
        repo,
        IndexedHistoryEvent::class.java,
    )
