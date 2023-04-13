package org.vechain.indexer.service

import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getBlock(number: Long): Block {
        val response =
            thorRest.get().uri("/blocks/$number?expanded=true").retrieve().bodyToMono(Block::class.java).block()
                ?: throw IndexerFullySynchronizedException("Block $number not found")

        logger.debug("Block $number found")

        return response
    }

    fun getBestBlock(): Block {
        val response =
            thorRest.get().uri("/blocks/best?expanded=true").retrieve().bodyToMono(Block::class.java).block()

        if (response?.number == null) {
            throw NotFoundException("Best block not found")
        }

        logger.debug("Best block found: ${response.number}")

        return response
    }

    fun getAccountCode(address: String): String? {
        val response =
            thorRest.get().uri("/accounts/$address/code").retrieve().bodyToMono(AccountCodeResponse::class.java).block()
                ?: throw NotFoundException("Account $address not found")

        logger.debug("Account $address found: $response")

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