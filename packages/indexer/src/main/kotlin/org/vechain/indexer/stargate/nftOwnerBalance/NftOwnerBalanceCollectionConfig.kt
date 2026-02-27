package org.vechain.indexer.stargate.nftOwnerBalance

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalance
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "nft-owner-balance")
@Configuration
open class NftOwnerBalanceCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, NftOwnerBalance::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.stargate-nft-owner-balance}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NFT_OWNER_BALANCE.NAME,
            NftOwnerBalance::class.java,
            version,
        )

        ensureCollection()

        ensureIndexes(
            listOf(
                "blockNumber_1" to
                    Index().on(IndexedDocument::blockNumber.name, Sort.Direction.ASC),
                "owner_1_blockNumber_-1" to
                    Index()
                        .on(NftOwnerBalance::owner.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
            )
        )
    }
}
