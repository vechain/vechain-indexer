package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive

@Profile("nft-events")
@Configuration
open class NftBlacklistConfig(mongoTemplate: MongoTemplate) :
    CollectionConfig(mongoTemplate, NFTBlacklist::class.java, NFTBlacklistArchive::class.java) {

    @PostConstruct
    override fun initCollection() {
        ensureCollection()

        ensureIndex(
            "nft_blacklist__id_1_blacklisted_1",
            Index().on("_id", Sort.Direction.ASC).on("blacklisted", Sort.Direction.ASC)
        )
    }
}
