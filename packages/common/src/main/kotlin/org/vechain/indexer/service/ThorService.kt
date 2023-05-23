package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.Clause
import org.vechain.indexer.model.rest.AccountCodeResponse
import org.vechain.indexer.model.rest.ExecuteCodeRequest
import org.vechain.indexer.model.rest.ExecuteCodeResponse
import org.vechain.indexer.model.rest.ExpandedBlockResponse

@Profile("block-indexer", "block-proxy")
@Service
class ThorService(private val thorRest: WebClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getBlock(number: Long): Block {
        val response = thorRest
            .get()
            .uri("/blocks/$number?expanded=true")
            .retrieve()
            .bodyToMono(ExpandedBlockResponse::class.java)
            .block()
            ?: throw BlockNotFoundException(blockNumber = number)

        logger.debug("Block $number found")

        return Block(response)
    }

    fun getBlock(blockId: String): Block {
        val response = thorRest
            .get()
            .uri("/blocks/$blockId?expanded=true")
            .retrieve()
            .bodyToMono(ExpandedBlockResponse::class.java)
            .block()
            ?: throw BlockNotFoundException(blockId = blockId)

        logger.debug("Block $blockId found")

        return Block(response)
    }

    fun getBestBlock(): Block {
        val response =
            thorRest.get().uri("/blocks/best?expanded=true").retrieve().bodyToMono(ExpandedBlockResponse::class.java)
                .block()
                ?: throw BlockNotFoundException("Best block not found", -1)

        logger.debug("Best block found: ${response.number}")

        return Block(response)
    }

    fun getAccountCode(address: String): String {
        val response =
            thorRest.get().uri("/accounts/$address/code").retrieve().bodyToMono(AccountCodeResponse::class.java).block()
                ?: throw NotFoundException("Account $address not found")

        logger.debug("Account $address found: $response")

        return response.code
    }

    fun executeReadOnlyCode(clauses: List<Clause>): List<ExecuteCodeResponse> {

        val bestBlock = getBestBlock()
        val blockRef = bestBlock.blockId.substring(0, 18)

        val request = ExecuteCodeRequest(clauses = clauses, blockRef = blockRef)

        return thorRest.post().uri("/accounts/*")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(request))
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<ExecuteCodeResponse>>() {})
            .block() ?: throw Exception("Empty response from Thor")
    }
}