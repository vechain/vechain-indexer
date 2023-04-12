package org.vechain.indexer.service

import org.apache.logging.log4j.LogManager
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.IndexerFullySynchronizedException
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.*

@Service
class ThorService(private val thorRest: WebClient) {

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

    fun getBestBlock(): Block {
        val response =
            thorRest.get().uri("/blocks/best?expanded=true").retrieve().bodyToMono(Block::class.java).block()

        if (response?.number == null) {
            logger.error("Best block not found")
            throw NotFoundException()
        }

        if (logger.isDebugEnabled) logger.debug("Best block# found: ${response.number}")

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

    fun executeReadOnlyCode(clauses: List<Clause>): List<ExecuteCodeResponse> {

        val bestBlock = getBestBlock()
        val blockRef = bestBlock.id?.substring(0, 18)
            ?: throw Exception("Block ref is null")

        val request = ExecuteCodeRequest(clauses = clauses, blockRef = blockRef)
        
        val res = thorRest.post().uri("/accounts/*")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(request))
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<ExecuteCodeResponse>>() {})
            .block() ?: throw Exception("Empty response from Thor")

        return res.toList()
    }
}