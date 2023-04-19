package org.vechain.e2e

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.net.URIBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.Transaction

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
    private val TX_RESPONSE_TYPE = object : ParameterizedTypeReference<List<Transaction>>() {}
    private val CONTRACT_RESPONSE_TYPE = object : ParameterizedTypeReference<List<Contract>>() {}
    private val NFT_RESPONSE_TYPE = object : ParameterizedTypeReference<List<NFT>>() {}


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

    fun getTransactions(address: String): List<Transaction> {
        return getRequest("$API_URL/transactions/${address}", TX_RESPONSE_TYPE)
    }

    fun getTransactions(address: String, includeDelegated: Boolean): List<Transaction> {
        return getRequest("$API_URL/transactions/${address}?includeDelegated=${includeDelegated}", TX_RESPONSE_TYPE)
    }

    fun getDelegatedTransactions(address: String): List<Transaction> {
        return getRequest("$API_URL/transactions/${address}/delegated", TX_RESPONSE_TYPE)
    }

    fun getNfts(address: String): List<NFT> {
        return getRequest("$API_URL/nfts/${address}", NFT_RESPONSE_TYPE)
    }

    fun getNfts(address: String, contractAddresses: List<String>): List<NFT> {
        val url = URIBuilder("$API_URL/nfts/${address}")
            .addParameter("contractAddresses", contractAddresses.joinToString(","))
            .toString()

        return getRequest(url, NFT_RESPONSE_TYPE)
    }

    fun getContracts(address: String): List<Contract> {
        return getRequest("$API_URL/contracts/${address}", CONTRACT_RESPONSE_TYPE)
    }

    private fun <T> getRequest(url: String, responseType: ParameterizedTypeReference<T>): T {
        val res = REST_TEMPLATE.exchange(url, HttpMethod.GET, null, responseType)

        if (!res.statusCode.is2xxSuccessful)
            throw Exception("GET failed $url")

        return res.body ?: throw Exception("No body found")
    }
}