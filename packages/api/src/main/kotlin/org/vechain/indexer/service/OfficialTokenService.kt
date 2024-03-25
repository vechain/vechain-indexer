package org.vechain.indexer.service

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.ParameterizedTypeReference
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.client.ThorClient
import org.vechain.indexer.model.NetworkType
import org.vechain.indexer.model.TokenRegistry

@Service
open class OfficialTokenService(
    private val thorClient: ThorClient,
    private val officialTokenRepoRest: WebClient
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private var officialTokenAddress = emptyList<String>()

    @EventListener(ApplicationReadyEvent::class)
    fun loadData() {
        val networkType =
            try {
                thorClient.getNetworkType()
            } catch (e: Exception) {
                logger.warn("Failed to get network type. Setting to OTHER. ${e.message}")
                NetworkType.OTHER
            }

        try {
            val tokenRegistry = getTokenRegistryInfoFromApi(networkType)

            logger.info(
                "${tokenRegistry.size} official tokens loaded from API for network $networkType"
            )

            officialTokenAddress = tokenRegistry.map { it.address }
        } catch (e: Exception) {
            logger.warn(
                "Token registry not loaded from API. Will load from local JSON. ${e.message}"
            )

            if (officialTokenAddress.isEmpty())
                getTokenRegistryInfoFromJson(networkType).let { token ->
                    officialTokenAddress = token.map { it.address }
                }
        }
    }

    // Load data every hour on the hour
    @Scheduled(cron = "0 0 * * * *")
    fun loadDataHourly() {
        loadData()
    }

    open fun getOfficialTokenAddresses(): List<String> {
        return officialTokenAddress
    }

    private fun getTokenRegistryInfoFromJson(networkType: NetworkType): List<TokenRegistry> {
        // Only main and test supported. Return empty list for other networks.
        if (networkType != NetworkType.MAIN && networkType != NetworkType.TEST) return emptyList()

        val path =
            Paths.get(
                javaClass.classLoader.getResource("token-registry/$networkType.json")?.toURI()
                    ?: throw Exception("Token registry not found")
            )

        val jsonData = String(Files.readAllBytes(path))

        return Json.decodeFromString(ListSerializer(TokenRegistry.serializer()), jsonData)
    }

    private fun getTokenRegistryInfoFromApi(networkType: NetworkType): List<TokenRegistry> {
        // Only main and test supported. Return empty list for other networks.
        if (networkType != NetworkType.MAIN && networkType != NetworkType.TEST) return emptyList()

        return officialTokenRepoRest
            .get()
            .uri("/$networkType.json")
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<TokenRegistry>>() {})
            .block() ?: throw Exception("Call to token registry API failed")
    }
}
