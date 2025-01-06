package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.model.ContractArchive
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.service.ArchiveService

@Configuration
open class ArchiveServiceConfig {
    @Bean
    @Qualifier("contractArchiveService")
    open fun contractArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<IndexedContract, ContractArchive> =
        ArchiveService(mongoTemplate, IndexedContract::class.java, ContractArchive::class.java)

    @Bean
    @Qualifier("nftArchiveService")
    open fun nftArchiveService(
        mongoTemplate: MongoTemplate
    ): ArchiveService<IndexedNFT, NFTArchive> =
        ArchiveService(mongoTemplate, IndexedNFT::class.java, NFTArchive::class.java)
}
