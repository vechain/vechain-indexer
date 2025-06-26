package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive
import org.vechain.indexer.model.stargate.VthoClaimedByAccount
import org.vechain.indexer.model.stargate.VthoClaimedByAccountArchive
import org.vechain.indexer.service.ArchiveService

@Configuration
open class ArchiveServiceConfig {

    @Bean
    @Qualifier("nftArchiveService")
    open fun nftArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<IndexedNFT, NFTArchive> =
        ArchiveService(mongoTemplate, IndexedNFT::class.java, NFTArchive::class.java)

    @Bean
    @Qualifier("nftBlacklistArchiveService")
    open fun nftBlacklistArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<NFTBlacklist, NFTBlacklistArchive> =
        ArchiveService(mongoTemplate, NFTBlacklist::class.java, NFTBlacklistArchive::class.java)

    @Bean
    @Qualifier("vthoClaimByAccountArchiveService")
    open fun vthoClaimByAccountArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive> =
        ArchiveService(
            mongoTemplate,
            VthoClaimedByAccount::class.java,
            VthoClaimedByAccountArchive::class.java,
        )
}
