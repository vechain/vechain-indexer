package org.vechain.indexer.nft

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("nfts")
@Configuration
open class NftBlacklistCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, NftBlacklist::class.java, NftBlacklistArchive::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.nft_blacklist}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                NftBlacklist::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(NftBlacklistArchive::class.java)

        ensureCollection()

        ensureIndex(
            "nft_blacklist__id_1_blacklisted_1",
            Index().on("_id", Sort.Direction.ASC).on("blacklisted", Sort.Direction.ASC),
        )
    }
}
