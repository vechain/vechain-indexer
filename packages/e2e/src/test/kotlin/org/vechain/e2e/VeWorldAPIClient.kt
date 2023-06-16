package org.vechain.e2e

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.vechain.indexer.model.*
import org.vechain.indexer.model.rest.PAGE_SIZE_LIMIT
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
    private val BLOCK_TYPE = object : ParameterizedTypeReference<IndexedBlock>() {}
    private val PAGINATED_CLAUSE_TYPE = object : ParameterizedTypeReference<List<IndexedClause>>() {}
    private val TX_TYPE = object : ParameterizedTypeReference<IndexedTransaction>() {}
    private val PAGINATED_TXS_TYPE = object : ParameterizedTypeReference<PaginatedResponse<IndexedTransaction>>() {}
    private val CONTRACT_TYPE = object : ParameterizedTypeReference<IndexedContract>() {}
    private val PAGINATED_CONTRACTS_TYPE = object : ParameterizedTypeReference<PaginatedResponse<IndexedContract>>() {}
    private val PAGINATED_NFTS_TYPE = object : ParameterizedTypeReference<PaginatedResponse<IndexedNFT>>() {}
    private val PAGINATED_NFT_CONTRACTS_TYPE = object : ParameterizedTypeReference<PaginatedResponse<String>>() {}
    private val PAGINATED_TRANSFER_EVENTS_TYPE =
        object : ParameterizedTypeReference<PaginatedResponse<IndexedTransferEvent>>() {}


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

    fun getBlock(revision: String): IndexedBlock {
        return getRequest("$API_URL/blocks/$revision", BLOCK_TYPE)
    }

    fun getClauses(
        address: String, page: Int = 0, size: Int = PAGE_SIZE_LIMIT
    ): List<IndexedClause> {
        return getRequest(
            "$API_URL/clauses?address=${address}&page=$page&size=$size",
            PAGINATED_CLAUSE_TYPE
        )
    }

    fun getContract(address: String): IndexedContract {
        return getRequest("$API_URL/contracts/$address", CONTRACT_TYPE)
    }

    fun getContractForCreator(
        address: String,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT
    ): PaginatedResponse<IndexedContract> {
        return getRequest("$API_URL/contracts?address=$address&page=$page&size=$size", PAGINATED_CONTRACTS_TYPE)
    }

    fun getNfts(
        address: String? = null,
        contractAddress: String? = null,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT
    ): PaginatedResponse<IndexedNFT> {
        return if (address != null && contractAddress != null)
            getRequest(
                "$API_URL/nfts?address=$address&contractAddress=$contractAddress&page=$page&size=$size",
                PAGINATED_NFTS_TYPE
            )
        else if (address != null)
            getRequest("$API_URL/nfts?address=$address&page=$page&size=$size", PAGINATED_NFTS_TYPE)
        else if (contractAddress != null)
            getRequest("$API_URL/nfts?contractAddress=$contractAddress&page=$page&size=$size", PAGINATED_NFTS_TYPE)
        else
            throw Exception("No address or contractAddress provided")
    }

    fun getNftContracts(
        owner: String,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT
    ): PaginatedResponse<String> {
        return getRequest(
            "$API_URL/nfts/contracts?owner=$owner&page=$page&size=$size",
            PAGINATED_NFT_CONTRACTS_TYPE
        )
    }

    fun getTransactionById(id: String): IndexedTransaction {
        return getRequest("$API_URL/transactions/${id}", TX_TYPE)
    }

    fun getTransactionsByOrigin(
        address: String,
        includeDelegated: Boolean = false,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT
    ): PaginatedResponse<IndexedTransaction> {
        return getRequest(
            "$API_URL/transactions?origin=${address}&includeDelegated=$includeDelegated&page=$page&size=$size",
            PAGINATED_TXS_TYPE
        )
    }

    fun getDelegatedTransactions(
        address: String,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT
    ): PaginatedResponse<IndexedTransaction> {
        return getRequest(
            "$API_URL/transactions/delegated?delegator=$address&page=$page&size=$size",
            PAGINATED_TXS_TYPE
        )
    }

    fun getTransferEvents(
        address: String? = null,
        tokenAddress: String? = null,
        page: Int = 0,
        size: Int = PAGE_SIZE_LIMIT
    ): PaginatedResponse<IndexedTransferEvent> {
        return if (address != null && tokenAddress != null)
            getRequest(
                "$API_URL/transfers?address=$address&tokenAddress=$tokenAddress&page=$page&size=$size",
                PAGINATED_TRANSFER_EVENTS_TYPE
            )
        else if (address != null)
            getRequest("$API_URL/transfers?address=$address&page=$page&size=$size", PAGINATED_TRANSFER_EVENTS_TYPE)
        else if (tokenAddress != null)
            getRequest(
                "$API_URL/transfers?tokenAddress=$tokenAddress&page=$page&size=$size",
                PAGINATED_TRANSFER_EVENTS_TYPE
            )
        else
            throw Exception("No address or tokenAddress provided")
    }

    private fun <T> getRequest(url: String, responseType: ParameterizedTypeReference<T>): T {
        val res = REST_TEMPLATE.exchange(url, HttpMethod.GET, null, responseType)

        if (!res.statusCode.is2xxSuccessful)
            throw Exception("GET failed $url")

        return res.body ?: throw Exception("No body found")
    }
}