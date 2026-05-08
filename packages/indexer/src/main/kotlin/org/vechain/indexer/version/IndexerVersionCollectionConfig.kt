package org.vechain.indexer.version

import kotlinx.coroutines.CoroutineScope
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.config.mongo.CollectionConfig

@Configuration
open class IndexerVersionCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexerVersion::class.java) {

    override fun initCollection() {
        ensureCollection()
        ensureIndexes(
            indexes =
                listOf(
                    "collectionName_1_unique" to
                        Index().on(IndexerVersion::collectionName.name, Sort.Direction.ASC).unique()
                ),
            partialFilter = null,
        )
    }
}
