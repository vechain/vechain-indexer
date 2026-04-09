package org.vechain.indexer.transfer

import jakarta.annotation.PostConstruct
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS

@Profile("transfers")
@Service
open class OfficialTokenService(private val networkDetectionService: NetworkDetectionService) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    internal open fun validateRegistryOnStartup() {
        val network = getNetworkType()
        if (network != VeChainNetwork.MAINNET && network != VeChainNetwork.TESTNET) {
            logger.info("Skipping token registry validation for unsupported network {}", network)
            return
        }

        try {
            val tokens = getTokenRegistryInfoFromJson(network)
            logger.info(
                "Validated local token registry for network {} with {} entries",
                network,
                tokens.size,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to load local token registry for network $network. " +
                    "Check bundled token-registry/$network.json",
                e,
            )
        }
    }

    /**
     * Gets the list of official token addresses, excluding VTHO. Results are cached for 1 hour.
     *
     * @return List of official token addresses (lowercase hex format)
     */
    @Cacheable(value = ["official_token_addresses"], key = "#root.methodName")
    open fun getOfficialTokenAddresses(): List<String> {
        val networkType = getNetworkType()
        val tokenRegistry = loadTokenRegistry(networkType)
        return filterTokenAddresses(tokenRegistry)
    }

    /**
     * Gets the network type from NetworkDetectionService, defaulting to CUSTOM on failure.
     *
     * @return VeChainNetwork
     */
    internal open fun getNetworkType(): VeChainNetwork {
        return try {
            networkDetectionService.detectBlocking().network
        } catch (e: Exception) {
            logger.warn("Failed to get network type. Setting to CUSTOM. ${e.message}")
            VeChainNetwork.CUSTOM
        }
    }

    /**
     * Loads token registry from local JSON.
     *
     * @param network The network type to load tokens for
     * @return List of TokenRegistry entries
     */
    internal open fun loadTokenRegistry(network: VeChainNetwork): List<TokenRegistry> {
        return getTokenRegistryInfoFromJson(network)
    }

    /**
     * Filters token registry to extract addresses, excluding VTHO.
     *
     * @param tokenRegistry List of TokenRegistry entries
     * @return List of token addresses
     */
    internal fun filterTokenAddresses(tokenRegistry: List<TokenRegistry>): List<String> {
        return tokenRegistry.map { it.address }.filter { it != VTHO_CONTRACT_ADDRESS }
    }

    /**
     * Loads token registry from local JSON file.
     *
     * @param network The network type to load tokens for
     * @return List of TokenRegistry entries
     * @throws Exception if the file cannot be found or parsed
     */
    internal open fun getTokenRegistryInfoFromJson(network: VeChainNetwork): List<TokenRegistry> {
        // Only main and test supported. Return empty list for other networks.
        if (network != VeChainNetwork.MAINNET && network != VeChainNetwork.TESTNET) {
            logger.debug("Network type $network not supported for token registry")
            return emptyList()
        }

        val path =
            Paths.get(
                javaClass.classLoader.getResource("token-registry/$network.json")?.toURI()
                    ?: throw Exception("Token registry not found for network: $network")
            )

        val jsonData = String(Files.readAllBytes(path))

        return Json.Default.decodeFromString(ListSerializer(TokenRegistry.serializer()), jsonData)
    }
}
