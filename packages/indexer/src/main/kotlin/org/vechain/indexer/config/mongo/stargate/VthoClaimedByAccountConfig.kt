package org.vechain.indexer.config.mongo.stargate

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.model.stargate.VthoClaimedByAccount
import org.vechain.indexer.model.stargate.VthoClaimedByAccountArchive
import org.vechain.indexer.service.IndexerVersionService

@Profile("stargate")
@Configuration
open class VthoClaimedByAccountConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        VthoClaimedByAccount::class.java,
        VthoClaimedByAccountArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.stargate_vtho_claimed_by_account}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                "stargate_vtho_claimed_by_account",
                version,
            )

        if (dropped)
            indexerVersionService.dropArchiveCollection("stargate_vtho_claimed_by_account_archives")

        ensureCollection()
    }
}
