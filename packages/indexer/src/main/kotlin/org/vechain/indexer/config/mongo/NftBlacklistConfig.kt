package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.model.NFTBlacklist

@Profile("nft-events")
@Configuration
open class NftBlacklistConfig(mongoTemplate: MongoTemplate) :
    CollectionConfig(mongoTemplate, NFTBlacklist::class.java) {

    @PostConstruct
    override fun initCollection() {
        ensureCollection()
    }
}
