package org.vechain.indexer.accounts

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.thor.HexUtils.normalise

@Profile("accounts", "vet-balance")
@Configuration
open class VetBalanceGenesisPreloader(
    private val vetBalanceRepository: VetBalanceRepository,
    private val objectMapper: ObjectMapper,
    private val networkDetectionService: NetworkDetectionService,
    @param:Value("\${indexer.genesis.vet-balances.resource:}")
    private val genesisVetBalancesResourcePath: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val resourceLoader = DefaultResourceLoader()

    @PostConstruct
    open fun preloadGenesisIfEmpty() {
        val existingCount = vetBalanceRepository.count()
        if (existingCount > 0) {
            logger.info(
                "Skipping genesis preload for VetBalance: table already has {} records.",
                existingCount,
            )
            return
        }

        val detected = networkDetectionService.detectBlocking()
        val genesisBlock = detected.genesisBlock
        val genesisResource = resolveGenesisVetBalancesResource(detected.network)

        if (!genesisResource.exists()) {
            logger.warn(
                "Skipping genesis preload for VetBalance: resource not found ({}).",
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

        vetBalanceRepository.saveAll(records)
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
