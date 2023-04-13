package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.IndexerFullySynchronizedException
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.AccountCodeResponse
import org.vechain.indexer.model.Block

@Service
class ThorService(private val thorRest: WebClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getBlock(number: Long): Block {
        val response =
            thorRest.get().uri("/blocks/$number?expanded=true").retrieve().bodyToMono(Block::class.java).block()
                ?: throw IndexerFullySynchronizedException("Block $number not found")

        logger.debug("Block $number found")

        return response
    }

    fun getBestBlockNumber(): Long {
        val response =
            thorRest.get().uri("/blocks/best?expanded=true").retrieve().bodyToMono(Block::class.java).block()

        if (response?.blockNumber == null)
            throw NotFoundException("Best block number not found")

        logger.debug("Best block found: ${response.blockNumber}")

        return response.blockNumber!!
    }

    fun getAccountCode(address: String): String? {
        val response =
            thorRest.get().uri("/accounts/$address/code").retrieve().bodyToMono(AccountCodeResponse::class.java).block()
                ?: throw NotFoundException("Account $address not found")

        logger.debug("Account $address found: $response")

        return response.code
    }
}