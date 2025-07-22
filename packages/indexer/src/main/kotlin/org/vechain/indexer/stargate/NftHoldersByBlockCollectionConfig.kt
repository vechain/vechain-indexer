package org.vechain.indexer.stargate

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.model.IndexedDocument
import org.vechain.indexer.model.stargate.NftHoldersByBlock
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate")
@Configuration
open class NftHoldersByBlockCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, NftHoldersByBlock::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.stargate_nft_holders_by_block}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            NftHoldersByBlock::class.java,
            version,
        )

        ensureCollection()

        // Ensure indexes
        ensureIndex(
            "blockTimestamp_1",
            Index().on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC),
        )
    }
}
