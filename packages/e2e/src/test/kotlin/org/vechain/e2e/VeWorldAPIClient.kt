package org.vechain.e2e

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.vechain.indexer.model.*

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
    private val BLOCK_TYPE = object : ParameterizedTypeReference<Block>() {}
    private val PAGINATED_CLAUSE_TYPE = object : ParameterizedTypeReference<List<WrappedClause>>() {}
    private val TX_TYPE = object : ParameterizedTypeReference<Transaction>() {}
    private val PAGINATED_TX_TYPE = object : ParameterizedTypeReference<List<Transaction>>() {}
    private val CONTRACT_TYPE = object : ParameterizedTypeReference<Contract>() {}
    private val PAGINATED_CONTRACT_TYPE = object : ParameterizedTypeReference<List<Contract>>() {}
    private val PAGINATED_NFT_TYPE = object : ParameterizedTypeReference<List<NFT>>() {}
    private val PAGINATED_TRANSFER_EVENT_TYPE =
        object : ParameterizedTypeReference<List<TransferEvent>>() {}


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

    fun getBlock(revision: String): Block {
        return getRequest("$API_URL/blocks?revision=$revision", BLOCK_TYPE)
    }

    fun getClauses(
        address: String, page: Int = 0, size: Int = Int.MAX_VALUE
    ): List<WrappedClause> {
        return getRequest(
            "$API_URL/clauses?address=${address}&page=$page&size=$size",
            PAGINATED_CLAUSE_TYPE
        )
    }

    fun getContract(address: String): Contract {
        return getRequest("$API_URL/contracts/address/$address", CONTRACT_TYPE)
    }

    fun getContractForCreator(address: String, page: Int = 0, size: Int = Int.MAX_VALUE): List<Contract> {
        return getRequest("$API_URL/contracts?address=$address&page=$page&size=$size", PAGINATED_CONTRACT_TYPE)
    }

    fun getNfts(
        address: String? = null,
        contractAddress: String? = null,
        page: Int = 0,
        size: Int = Int.MAX_VALUE
    ): List<NFT> {
        return if (address != null && contractAddress != null)
            getRequest(
                "$API_URL/nfts?address=$address&contractAddress=$contractAddress&page=$page&size=$size",
                PAGINATED_NFT_TYPE
            )
        else if (address != null)
            getRequest("$API_URL/nfts?address=$address&page=$page&size=$size", PAGINATED_NFT_TYPE)
        else if (contractAddress != null)
            getRequest("$API_URL/nfts?contractAddress=$contractAddress&page=$page&size=$size", PAGINATED_NFT_TYPE)
        else
            throw Exception("No address or contractAddress provided")
    }

    fun getTransactionById(id: String): Transaction {
        return getRequest("$API_URL/transactions?id=${id}", TX_TYPE)
    }

    fun getTransactionsByOrigin(
        address: String,
        includeDelegated: Boolean = false,
        page: Int = 0,
        size: Int = Int.MAX_VALUE
    ): List<Transaction> {
        return getRequest(
            "$API_URL/transactions/origin?address=${address}&includeDelegated=$includeDelegated&page=$page&size=$size",
            PAGINATED_TX_TYPE
        )
    }

    fun getDelegatedTransactions(
        address: String,
        page: Int = 0,
        size: Int = Int.MAX_VALUE
    ): List<Transaction> {
        return getRequest("$API_URL/transactions/delegated?address=$address&page=$page&size=$size", PAGINATED_TX_TYPE)
    }

    fun getTransferEvents(
        address: String? = null,
        tokenAddress: String? = null,
        page: Int = 0,
        size: Int = Int.MAX_VALUE
    ): List<TransferEvent> {
        return if (address != null && tokenAddress != null)
            getRequest(
                "$API_URL/transfers?address=$address&tokenAddress=$tokenAddress&page=$page&size=$size",
                PAGINATED_TRANSFER_EVENT_TYPE
            )
        else if (address != null)
            getRequest("$API_URL/transfers?address=$address&page=$page&size=$size", PAGINATED_TRANSFER_EVENT_TYPE)
        else if (tokenAddress != null)
            getRequest(
                "$API_URL/transfers?tokenAddress=$tokenAddress&page=$page&size=$size",
                PAGINATED_TRANSFER_EVENT_TYPE
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