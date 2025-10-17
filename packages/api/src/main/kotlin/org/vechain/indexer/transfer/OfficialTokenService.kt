package org.vechain.indexer.transfer

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.client.ThorClient
import org.vechain.indexer.config.NetworkType
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS

@Profile("transfers")
@Service
open class OfficialTokenService(
    private val thorClient: ThorClient,
    private val officialTokenRepoRest: WebClient,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

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
     * Gets the network type from ThorClient, defaulting to OTHER on failure.
     *
     * @return NetworkType
     */
    internal open fun getNetworkType(): NetworkType {
        return try {
            thorClient.getNetworkType()
        } catch (e: Exception) {
            logger.warn("Failed to get network type. Setting to OTHER. ${e.message}")
            NetworkType.OTHER
        }
    }

    /**
     * Loads token registry from API with fallback to local JSON.
     *
     * @param networkType The network type to load tokens for
     * @return List of TokenRegistry entries
     */
    internal open fun loadTokenRegistry(networkType: NetworkType): List<TokenRegistry> {
        return try {
            val tokens = getTokenRegistryInfoFromApi(networkType)
            logger.info("${tokens.size} official tokens loaded from API for network $networkType")
            tokens
        } catch (e: Exception) {
            logger.warn(
                "Token registry not loaded from API. Will load from local JSON. ${e.message}"
            )
            getTokenRegistryInfoFromJson(networkType)
        }
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
     * @param networkType The network type to load tokens for
     * @return List of TokenRegistry entries
     * @throws Exception if the file cannot be found or parsed
     */
    internal open fun getTokenRegistryInfoFromJson(networkType: NetworkType): List<TokenRegistry> {
        // Only main and test supported. Return empty list for other networks.
        if (networkType != NetworkType.MAIN && networkType != NetworkType.TEST) {
            logger.debug("Network type $networkType not supported for token registry")
            return emptyList()
        }

        val path =
            Paths.get(
                javaClass.classLoader.getResource("token-registry/$networkType.json")?.toURI()
                    ?: throw Exception("Token registry not found for network: $networkType")
            )

        val jsonData = String(Files.readAllBytes(path))

        return Json.Default.decodeFromString(ListSerializer(TokenRegistry.serializer()), jsonData)
    }

    /**
     * Loads token registry from remote API.
     *
     * @param networkType The network type to load tokens for
     * @return List of TokenRegistry entries
     * @throws Exception if the API call fails or returns null
     */
    internal open fun getTokenRegistryInfoFromApi(networkType: NetworkType): List<TokenRegistry> {
        // Only main and test supported. Return empty list for other networks.
        if (networkType != NetworkType.MAIN && networkType != NetworkType.TEST) {
            logger.debug("Network type $networkType not supported for token registry API")
            return emptyList()
        }

        return officialTokenRepoRest
            .get()
            .uri("/$networkType.json")
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<TokenRegistry>>() {})
            .block()
            ?: throw Exception("Call to token registry API failed for network: $networkType")
    }
}
