package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BlacklistableRepository
import org.vechain.indexer.nft.NftBlacklistRepository

@Profile("history")
@Component
open class HistoryRepositoryImpl(mongoTemplate: MongoTemplate, repo: NftBlacklistRepository) :
    BlacklistableRepository<IndexedHistoryEvent>(
        mongoTemplate,
        repo,
        IndexedHistoryEvent::class.java,
    )
