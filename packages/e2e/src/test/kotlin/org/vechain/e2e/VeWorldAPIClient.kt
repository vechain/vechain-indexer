package org.vechain.e2e

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.rest.PAGE_SIZE_LIMIT
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.transaction.IndexedTransaction
import org.vechain.indexer.transfer.IndexedTransferEvent
import org.vechain.indexer.transfer.TransferEventType

object VeWorldAPIClient {

    /** Client Config */
    private const val BASE_URL = "http://localhost:8080"
    private const val INDEXER_URL = "http://localhost:8081"
    private const val API_URL = "http://localhost:8080/api/v1/"

    private val REST_TEMPLATE = RestTemplate()

    /** Response Types */
    private val TX_TYPE = object : ParameterizedTypeReference<IndexedTransaction>() {}
    private val PAGINATED_TXS_TYPE =
        object : ParameterizedTypeReference<PaginatedResponse<IndexedTransaction>>() {}
    private val PAGINATED_NFTS_TYPE =
        object : ParameterizedTypeReference<PaginatedResponse<IndexedNft>>() {}
    private val PAGINATED_NFT_CONTRACTS_TYPE =
        object : ParameterizedTypeReference<PaginatedResponse<String>>() {}
    private val PAGINATED_TRANSFER_EVENTS_TYPE =
        object : ParameterizedTypeReference<PaginatedResponse<IndexedTransferEvent>>() {}
    private val NFTS_TYPE =
        object : ParameterizedTypeReference<List<org.vechain.indexer.nft.IndexedNft>>() {}
    private val TRANSFER_EVENTS_TYPE =
        object : ParameterizedTypeReference<List<IndexedTransferEvent>>() {}

    data class HealthCheckComponent(
        val status: String = "DOWN",
        val details: Map<String, Any> = emptyMap(),
    )

    data class HealthCheckResponse(
        val status: String = "DOWN",
        val components: Map<String, HealthCheckComponent> = emptyMap(),
    )

    fun performHealthCheck() {

        val res =
            REST_TEMPLATE.exchange(
                "$BASE_URL/actuator/health",
                HttpMethod.GET,
                null,
                HealthCheckResponse::class.java,
            )
        if (!res.statusCode.is2xxSuccessful)
            throw Exception("Health failed with status code ${res.statusCode}")

        if (res.body?.status != "UP")
            throw Exception("Health failed with status ${res.body?.status}")

        val mongoStatus = res.body?.components?.get("mongo")?.status

        if (mongoStatus != "UP") throw Exception("Health failed with status $mongoStatus")
    }

    fun performIndexerHealthCheck(indexerComponent: String) {

        for (i in 1..30) {
            try {
                val res =
                    REST_TEMPLATE.exchange(
                        "$INDEXER_URL/actuator/health",
                        HttpMethod.GET,
                        null,
                        HealthCheckResponse::class.java,
                    )

                if (!res.statusCode.is2xxSuccessful || res.body?.status != "UP")
                    throw Exception("Health status failed")

                val indexerStatus =
                    res.body?.components?.get("indexer")?.details?.get("IndexersHealth")
                        as List<LinkedHashMap<String, String>>

                val syncStatus =
                    indexerStatus.find { it["indexerName"] == indexerComponent }?.get("syncStatus")

                if (syncStatus != "FULLY_SYNCED")
                    throw Exception("Health failed with status $syncStatus")

                return
            } catch (_: Exception) {
                Thread.sleep(1_000)
            }
        }

        throw Exception("Indexer health check failed")
    }

    fun getNfts(
        address: String? = null,
        contractAddress: String? = null,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT,
    ): PaginatedResponse<IndexedNft> {
        return if (address != null && contractAddress != null)
            getRequest(
                "$API_URL/nfts?address=$address&contractAddress=$contractAddress&page=$page&size=$size",
                PAGINATED_NFTS_TYPE,
            )
        else if (address != null)
            getRequest("$API_URL/nfts?address=$address&page=$page&size=$size", PAGINATED_NFTS_TYPE)
        else if (contractAddress != null)
            getRequest(
                "$API_URL/nfts?contractAddress=$contractAddress&page=$page&size=$size",
                PAGINATED_NFTS_TYPE,
            )
        else throw Exception("No address or contractAddress provided")
    }

    fun getNftContracts(
        owner: String,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT,
    ): PaginatedResponse<String> {
        return getRequest(
            "$API_URL/nfts/contracts?owner=$owner&page=$page&size=$size",
            PAGINATED_NFT_CONTRACTS_TYPE,
        )
    }

    fun getTransactionById(id: String): IndexedTransaction {
        return getRequest("$API_URL/transactions/${id}?expanded=true", TX_TYPE)
    }

    fun getTransactionsByOrigin(
        address: String,
        includeDelegated: Boolean = false,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT,
    ): PaginatedResponse<IndexedTransaction> {
        return getRequest(
            "$API_URL/transactions?origin=${address}&includeDelegated=$includeDelegated&page=$page&size=$size&expanded=true",
            PAGINATED_TXS_TYPE,
        )
    }

    fun getDelegatedTransactions(
        address: String,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT,
    ): PaginatedResponse<IndexedTransaction> {
        return getRequest(
            "$API_URL/transactions/delegated?delegator=$address&page=$page&size=$size&expanded=true",
            PAGINATED_TXS_TYPE,
        )
    }

    fun getTransferEvents(
        address: String? = null,
        tokenAddress: String? = null,
        eventType: TransferEventType? = null,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT,
    ): PaginatedResponse<IndexedTransferEvent> {
        val eventTypeParam = eventType?.let { "&eventType=$it" } ?: ""
        return if (address != null && tokenAddress != null)
            getRequest(
                "$API_URL/transfers?address=$address&tokenAddress=$tokenAddress$eventTypeParam&page=$page&size=$size",
                PAGINATED_TRANSFER_EVENTS_TYPE,
            )
        else if (address != null)
            getRequest(
                "$API_URL/transfers?address=$address$eventTypeParam&page=$page&size=$size",
                PAGINATED_TRANSFER_EVENTS_TYPE,
            )
        else if (tokenAddress != null)
            getRequest(
                "$API_URL/transfers?tokenAddress=$tokenAddress$eventTypeParam&page=$page&size=$size",
                PAGINATED_TRANSFER_EVENTS_TYPE,
            )
        else throw Exception("No address or tokenAddress provided")
    }

    fun getTransferEventsFrom(
        address: String,
        tokenAddress: String? = null,
        eventType: TransferEventType? = null,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT,
    ): PaginatedResponse<IndexedTransferEvent> {
        val tokenParam = tokenAddress?.let { "&tokenAddress=$it" } ?: ""
        val eventTypeParam = eventType?.let { "&eventType=$it" } ?: ""
        return getRequest(
            "$API_URL/transfers/from?address=$address$tokenParam$eventTypeParam&page=$page&size=$size",
            PAGINATED_TRANSFER_EVENTS_TYPE,
        )
    }

    fun getTransferEventsTo(
        address: String,
        tokenAddress: String? = null,
        eventType: TransferEventType? = null,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT,
    ): PaginatedResponse<IndexedTransferEvent> {
        val tokenParam = tokenAddress?.let { "&tokenAddress=$it" } ?: ""
        val eventTypeParam = eventType?.let { "&eventType=$it" } ?: ""
        return getRequest(
            "$API_URL/transfers/to?address=$address$tokenParam$eventTypeParam&page=$page&size=$size",
            PAGINATED_TRANSFER_EVENTS_TYPE,
        )
    }

    fun getNftTransfers(): List<IndexedTransferEvent> {
        return getRequest("$API_URL/e2e/transfers", TRANSFER_EVENTS_TYPE)
    }

    fun getNfts(): List<org.vechain.indexer.nft.IndexedNft> {
        return getRequest("$API_URL/e2e/nfts", NFTS_TYPE)
    }

    private fun <T> getRequest(url: String, responseType: ParameterizedTypeReference<T>): T {
        val res = REST_TEMPLATE.exchange(url, HttpMethod.GET, null, responseType)

        if (!res.statusCode.is2xxSuccessful) throw Exception("GET failed $url")

        return res.body ?: throw Exception("No body found")
    }
}
