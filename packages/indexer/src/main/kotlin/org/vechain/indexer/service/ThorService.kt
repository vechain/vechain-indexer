package org.vechain.indexer.service

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.IndexerFullySynchronizedException
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.AccountCodeResponse
import org.vechain.indexer.model.Block

@Service
class ThorService(val thorRest: WebClient) {

    private val logger = LogManager.getLogger(this::class.simpleName)

    fun getBlock(number: Long): Block {
        val response =
            thorRest.get().uri("/blocks/$number?expanded=true").retrieve().bodyToMono(Block::class.java).block()

        if (response == null) {
            logger.error("Block $number not found")
            throw IndexerFullySynchronizedException()
        }

        if (logger.isDebugEnabled) logger.debug("Block $number found: ${response.id}")

        return response
    }

    fun getAccountCode(address: String): String? {
        val response =
            thorRest.get().uri("/accounts/$address/code").retrieve().bodyToMono(AccountCodeResponse::class.java).block()

        if (response == null) {
            logger.error("Account $address not found")
            throw NotFoundException()
        }

        if (logger.isDebugEnabled) logger.debug("Account $address found: $response")

        return response.code
    }
}