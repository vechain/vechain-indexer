package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.NFTBlacklist

@Profile("nft-events")
@Configuration
open class NftBlacklistConfig(mongoTemplate: MongoTemplate) :
    CollectionConfig(mongoTemplate, NFTBlacklist::class.java) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initCollection() {

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndex(
            "nft_blacklist_contractAddress_1",
            Index().on("contractAddress", Sort.Direction.ASC)
        )
    }
}
