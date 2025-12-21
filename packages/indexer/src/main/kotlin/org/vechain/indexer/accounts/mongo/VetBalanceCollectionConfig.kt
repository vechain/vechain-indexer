package org.vechain.indexer.accounts.mongo

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.count
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.insert
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.VetBalance
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.thor.HexUtils.normalise
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "vet-balance")
@Configuration
open class VetBalanceCollectionConfig(
    private val mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    private val objectMapper: ObjectMapper,
    private val networkDetectionService: NetworkDetectionService,
    @param:Value("\${indexer.genesis.vet-balances.resource:}")
    private val genesisVetBalancesResourcePath: String,
) : CollectionConfig(mongoTemplate, appCoroutineScope, VetBalance::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val resourceLoader = DefaultResourceLoader()

    @Value("\${indexer.version.vet-balance:1}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VET_BALANCE_INDEXER,
            VetBalance::class.java,
            version,
        )

        ensureCollection()

        preloadGenesisIfCollectionEmpty()

        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                // Supports API query by address + timestamp range with default sort by newest.
                "blockNumber_-1" to
                    Index().on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
                "address_1_blockTimestamp_-1" to
                    Index()
                        .on(VetBalance::address.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.DESC),
            )
        )
    }

    private fun preloadGenesisIfCollectionEmpty() {
        val existingCount = mongoTemplate.count<VetBalance>(Query())
        if (existingCount > 0) {
            logger.info(
                "Skipping genesis preload for ${modelObj.simpleName}: collection already has {} records.",
                existingCount,
            )
            return
        }

        val detected = networkDetectionService.detectBlocking()
        val genesisBlock = detected.genesisBlock
        val genesisResource = resolveGenesisVetBalancesResource(detected.network)

        if (!genesisResource.exists()) {
            logger.warn(
                "Skipping genesis preload for ${modelObj.simpleName}: resource not found ({}).",
                genesisResource.description,
            )
            return
        }

        val genesis =
            genesisResource.inputStream.use {
                objectMapper.readValue(it, GenesisVetBalancesFile::class.java)
            }

        val computedTotalSupply =
            genesis.allocations.fold(BigInteger.ZERO) { acc, allocation ->
                acc + BigInteger(allocation.balance)
            }

        val normalizedAllocations =
            genesis.allocations.map { allocation ->
                GenesisAllocation(
                    address = normalise(allocation.address),
                    balance = allocation.balance,
                )
            }

        val duplicates =
            normalizedAllocations.groupBy { it.address }.filterValues { it.size > 1 }.keys
        check(duplicates.isEmpty()) {
            "Invalid genesis VET balances: duplicate addresses found: ${duplicates.joinToString(", ")}"
        }

        val records =
            normalizedAllocations.map { allocation ->
                VetBalance(
                    address = allocation.address,
                    blockId = genesisBlock.id,
                    blockNumber = 0L,
                    blockTimestamp = genesisBlock.timestamp,
                    balance = BigInteger(allocation.balance),
                )
            }

        mongoTemplate.insert<VetBalance>(records)
        logger.info(
            "Preloaded {} genesis VET balances for network={} (launchTime={}, totalSupply={}).",
            records.size,
            genesis.network,
            genesis.launchTime,
            computedTotalSupply,
        )
    }

    private fun resolveGenesisVetBalancesResource(network: VeChainNetwork): Resource {
        val configured = genesisVetBalancesResourcePath.trim()
        if (configured.isNotEmpty()) {
            return resourceLoader.getResource(configured)
        }

        val resourcePath =
            when (network) {
                VeChainNetwork.MAINNET -> "classpath:genesis/vet-balances/mainnet.json"
                VeChainNetwork.TESTNET -> "classpath:genesis/vet-balances/testnet.json"
                VeChainNetwork.CUSTOM -> "classpath:genesis/vet-balances/custom.json"
            }

        logger.info("Detected network={} selecting {}", network, resourcePath)
        return resourceLoader.getResource(resourcePath)
    }

    private data class GenesisVetBalancesFile(
        val network: String,
        val launchTime: Long,
        val allocations: List<GenesisAllocation>,
    )

    private data class GenesisAllocation(val address: String, val balance: String)
}
