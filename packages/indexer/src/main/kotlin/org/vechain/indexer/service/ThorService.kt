package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.rest.AccountCodeResponse
import org.vechain.indexer.model.rest.ExecuteCodeRequest
import org.vechain.indexer.model.rest.ExecuteCodeResponse
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause

@Service
class ThorService(private val thorRest: WebClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getBlock(number: Long): Block {
        val block =
            thorRest
                .get()
                .uri("/blocks/$number?expanded=true")
                .retrieve()
                .bodyToMono(Block::class.java)
                .block() ?: throw BlockNotFoundException(message = "Block $number not found")

        if (logger.isDebugEnabled) logger.debug("Block $number found")

        return block
    }

    fun getBestBlock(): Block {
        val block =
            thorRest
                .get()
                .uri("/blocks/best?expanded=true")
                .retrieve()
                .bodyToMono(Block::class.java)
                .block() ?: throw NotFoundException("Best block not found")

        if (logger.isDebugEnabled) logger.debug("Best block found: ${block.number}")

        return block
    }

    fun getFinalisedBlock(): Block {
        val block =
            thorRest
                .get()
                .uri("/blocks/finalized?expanded=true")
                .retrieve()
                .bodyToMono(Block::class.java)
                .block() ?: throw NotFoundException("Best block not found")

        if (logger.isDebugEnabled) logger.debug("Best block found: ${block.number}")

        return block
    }

    fun getAccountCode(address: String): String {
        val response =
            thorRest
                .get()
                .uri("/accounts/$address/code")
                .retrieve()
                .bodyToMono(AccountCodeResponse::class.java)
                .block() ?: throw NotFoundException("Account $address not found")

        if (logger.isDebugEnabled) logger.debug("Account $address found: $response")

        return response.code
    }

    fun executeReadOnlyCode(clauses: List<Clause>): List<ExecuteCodeResponse> {

        val bestBlock = getBestBlock()
        val blockRef = bestBlock.id.substring(0, 18)

        val request = ExecuteCodeRequest(clauses = clauses, blockRef = blockRef)

        return thorRest
            .post()
            .uri("/accounts/*")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(request))
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<List<ExecuteCodeResponse>>() {})
            .block() ?: throw Exception("Empty response from Thor")
    }
}
