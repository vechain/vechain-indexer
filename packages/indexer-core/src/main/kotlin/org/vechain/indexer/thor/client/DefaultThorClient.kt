package org.vechain.indexer.thor.client

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.JsonUtils

/**
 * Default implementation of the {@link org.vechain.indexer.thor.client.ThorClient.class ThorClient}
 * using the Fuel HTTP library and Jackson JSON mapper.
 *
 * @see <a href="https://github.com/kittinunf/fuel">Fuel Library</a>
 */
class DefaultThorClient(
    private val baseUrl: String,
    private vararg val headers: Pair<String, Any>
) : ThorClient {

    private val objectMapper = JsonUtils.mapper

    override suspend fun getBlock(blockNumber: Long): Block =
        withContext(Dispatchers.IO) {
            val (_, _, result) =
                Fuel.get("${baseUrl}/blocks/$blockNumber?expanded=true")
                    .appendHeader(*headers)
                    .response()

            val responseBody =
                when (result) {
                    is Result.Success -> result.get().toString(Charsets.UTF_8)
                    is Result.Failure ->
                        throw Exception(
                            "Get block $blockNumber request failed with error: ${result.error}"
                        )
                    else -> null
                }

            if (responseBody.isNullOrEmpty() || responseBody.trim() == "null")
                throw BlockNotFoundException("Block $blockNumber not found")

            return@withContext objectMapper.readValue(responseBody, Block::class.java)
        }

    override suspend fun getBestBlock(): Block =
        withContext(Dispatchers.IO) {
            val (_, _, result) =
                Fuel.get("${baseUrl}/blocks/best?expanded=true").appendHeader(*headers).response()

            val responseBody =
                when (result) {
                    is Result.Success -> result.get().toString(Charsets.UTF_8)
                    is Result.Failure ->
                        throw Exception("Get best block request failed with error: ${result.error}")
                    else -> null
                }

            return@withContext objectMapper.readValue(responseBody, Block::class.java)
        }
}
