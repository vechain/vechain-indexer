package org.vechain.indexer.config.genesis

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.thor.HexUtils.normalise
import org.vechain.indexer.thor.model.Block

/**
 * Service that loads genesis VET balance allocations from JSON resource files. This centralizes the
 * genesis loading logic shared by multiple collection configs.
 */
@Service
class GenesisVetBalanceLoader(
    private val objectMapper: ObjectMapper,
    private val networkDetectionService: NetworkDetectionService,
    @Value("\${indexer.genesis.vet-balances.resource:}")
    private val genesisVetBalancesResourcePath: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val resourceLoader = DefaultResourceLoader()

    /** Represents the structure of the genesis VET balances JSON file. */
    data class GenesisVetBalancesFile(
        val network: String,
        val launchTime: Long,
        val allocations: List<GenesisAllocation>,
    )

    /** Represents a single allocation in the genesis file. */
    data class GenesisAllocation(val address: String, val balance: String)

    /** Result of loading genesis allocations, including network metadata and the genesis block. */
    data class LoadedGenesis(
        val network: String,
        val launchTime: Long,
        val genesisBlock: Block,
        val allocations: List<GenesisAllocation>,
    )

    /**
     * Loads genesis allocations from the appropriate resource file based on the detected network.
     * Normalizes addresses and validates for duplicates.
     *
     * @return LoadedGenesis if successful, null if the resource doesn't exist
     * @throws IllegalStateException if duplicate addresses are found
     */
    fun loadGenesisAllocations(): LoadedGenesis? {
        val detected = networkDetectionService.detectBlocking()
        val genesisResource = resolveGenesisVetBalancesResource(detected.network)

        if (!genesisResource.exists()) {
            logger.warn("Genesis resource not found: {}", genesisResource.description)
            return null
        }

        val genesis =
            genesisResource.inputStream.use {
                objectMapper.readValue(it, GenesisVetBalancesFile::class.java)
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

        return LoadedGenesis(
            network = genesis.network,
            launchTime = genesis.launchTime,
            genesisBlock = detected.genesisBlock,
            allocations = normalizedAllocations,
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
}
