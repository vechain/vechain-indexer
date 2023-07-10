package org.vechain.indexer

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.utils.JsonUtils
import org.vechain.thor.model.Block

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
                        throw Exception("Request failed with error: ${result.error}")
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
                        throw Exception("Request failed with error: ${result.error}")
                    else -> null
                }

            return@withContext objectMapper.readValue(responseBody, Block::class.java)
        }
}
