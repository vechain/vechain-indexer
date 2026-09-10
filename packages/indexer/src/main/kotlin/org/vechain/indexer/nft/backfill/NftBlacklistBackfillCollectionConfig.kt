package org.vechain.indexer.nft.backfill

import kotlinx.coroutines.CoroutineScope
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.mongo.CollectionConfig

@Profile("nfts", "history")
@Configuration
open class NftBlacklistBackfillCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, NftBlacklistBackfillTask::class.java) {
    override fun initCollection() {
        ensureCollection()
        ensureIndexes(
            indexes =
                listOf(
                    buildIndex(
                        NftBlacklistBackfillTask::status.name to Sort.Direction.ASC,
                        NftBlacklistBackfillTask::updatedAt.name to Sort.Direction.ASC,
                    )
                ),
            partialFilter = null,
        )
    }
}
