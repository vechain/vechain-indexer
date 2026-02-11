package org.vechain.indexer.stargate.vthoClaimed

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vtho-claimed-by-account")
@Configuration
open class VthoClaimedByAccountCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        VthoClaimedByAccount::class.java,
        hasArchives = true,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.stargate-vtho-claimed-by-account}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VTHO_CLAIMED_BY_ACCOUNT.NAME,
            VthoClaimedByAccount::class.java,
            version,
        )

        ensureCollection()
    }
}
