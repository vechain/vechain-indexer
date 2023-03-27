package org.vechain.indexer.service

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.IndexerFullySynchronizedException
import org.vechain.indexer.model.Block

@Service
class ThorService(val thorRest: WebClient) {

    fun getBlock(number: Long): Block {
        val response = thorRest.get().uri("/blocks/$number?expanded=true").retrieve().bodyToMono(Block::class.java).block()

        if (response == null) {
            logger.error("Block $number not found")
            throw IndexerFullySynchronizedException()
        }

        if (logger.isDebugEnabled) logger.debug("Block $number found: ${response.id}")

        return response
    }

    companion object {
        private val logger = LogManager.getLogger(ThorService::class.java)
    }
}