package org.vechain.indexer.history

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.version.IndexerVersionService

@Profile("history")
@Configuration
open class HistoryCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedHistoryEvent::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.history}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.HISTORY,
            IndexedHistoryEvent::class.java,
            version,
        )

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "appId_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::appId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "blockNumber_1" to
                    Index().on(IndexedHistoryEvent::blockNumber.name, Sort.Direction.ASC),
                "contractAddress_1" to
                    Index().on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC),
                "isBlacklisted_1" to
                    Index().on(IndexedHistoryEvent::isBlacklisted.name, Sort.Direction.ASC),
                "to_1_contractAddress_1_blockTimestamp_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "from_1_contractAddress_1_blockTimestamp_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "origin_1_contractAddress_1_blockTimestamp_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "from_1_blockTimestamp_-1_eventName_1_contractAddress_1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "tokenId_1_blockTimestamp_-1_eventName_1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::tokenId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "to_1_blockTimestamp_-1_eventName_1_contractAddress_1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "origin_1_blockTimestamp_-1_eventName_1_contractAddress_1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "eventName_1_to_1_blockTimestamp_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "owner_1_blockTimestamp_-1_eventName_1_contractAddress_1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::owner.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "appId_1_eventName_1_to_1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::appId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "gasPayer_1_blockTimestamp_-1_eventName_1_contractAddress_1_isBlacklisted_1" to
                    Index()
                        .on(IndexedHistoryEvent::gasPayer.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
            )
        )
    }
}
