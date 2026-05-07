package org.vechain.indexer.stargate.nftOwnerBalance

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
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

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NFT_OWNER_BALANCE.NAME,
            NftOwnerBalance::class.java,
            version,
        )
        ensureCollection()
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.ASC),
                buildIndex(
                    NftOwnerBalance::owner.name to Sort.Direction.ASC,
                    IndexedDocument::blockNumber.name to Sort.Direction.DESC,
                ),
            )
        )
    }
}
