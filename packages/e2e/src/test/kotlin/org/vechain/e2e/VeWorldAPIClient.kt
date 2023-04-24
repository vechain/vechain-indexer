package org.vechain.e2e

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.Transaction
import org.vechain.indexer.model.rest.PaginatedResponse

object VeWorldAPIClient {

    /**
     * Client Config
     */
    private const val BASE_URL = "http://localhost:8080"
    private const val API_URL = "http://localhost:8080/api/v1/"
    private val REST_TEMPLATE = RestTemplate()

    /**
     * Response Types
     */
    private val TX_TYPE = object : ParameterizedTypeReference<Transaction>() {}
    private val PAGINATED_TX_TYPE = object : ParameterizedTypeReference<PaginatedResponse<List<Transaction>>>() {}
    private val CONTRACT_TYPE = object : ParameterizedTypeReference<Contract>() {}
    private val PAGINATED_NFT_TYPE = object : ParameterizedTypeReference<PaginatedResponse<List<NFT>>>() {}


    fun performHealthCheck() {

        data class HealthCheckComponent(
            val status: String = "DOWN",
            val details: Map<String, String> = emptyMap()
        )

        data class HealthCheckResponse(
            val status: String = "DOWN",
            val components: Map<String, HealthCheckComponent> = emptyMap()
        )

        val res =
            REST_TEMPLATE.exchange("$BASE_URL/actuator/health", HttpMethod.GET, null, HealthCheckResponse::class.java)
        if (!res.statusCode.is2xxSuccessful)
            throw Exception("Health failed with status code ${res.statusCode}")

        if (res.body?.status != "UP")
            throw Exception("Health failed with status ${res.body?.status}")

        val mongoStatus = res.body?.components?.get("mongo")?.status

        if (mongoStatus != "UP")
            throw Exception("Health failed with status $mongoStatus")
    }

    fun getTransactionById(id: String): Transaction {
        return getRequest("$API_URL/transactions?id=$id", TX_TYPE)
    }

    fun getTransactionsByOrigin(
        address: String,
        includeDelegated: Boolean = false
    ): PaginatedResponse<List<Transaction>> {
        return getRequest(
            "$API_URL/transactions/origin?address=$address&includeDelegated=$includeDelegated",
            PAGINATED_TX_TYPE
        )
    }

    fun getDelegatedTransactions(address: String): PaginatedResponse<List<Transaction>> {
        return getRequest("$API_URL/transactions/delegated?address=$address", PAGINATED_TX_TYPE)
    }

    fun getNfts(address: String): PaginatedResponse<List<NFT>> {
        return getRequest("$API_URL/nfts?address$address", PAGINATED_NFT_TYPE)
    }

    fun getNfts(address: String, contractAddress: String): PaginatedResponse<List<NFT>> {
        return getRequest("$API_URL/nfts?address=$address&contractAddresses=$contractAddress", PAGINATED_NFT_TYPE)
    }

    fun getContract(address: String): Contract {
        return getRequest("$API_URL/contracts?address=$address", CONTRACT_TYPE)
    }

    private fun <T> getRequest(url: String, responseType: ParameterizedTypeReference<T>): T {
        val res = REST_TEMPLATE.exchange(url, HttpMethod.GET, null, responseType)

        if (!res.statusCode.is2xxSuccessful)
            throw Exception("GET failed $url")

        return res.body ?: throw Exception("No body found")
    }
}