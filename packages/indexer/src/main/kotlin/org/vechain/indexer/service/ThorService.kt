package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.rest.ExecuteCodeRequest
import org.vechain.indexer.model.rest.ExecuteCodeResponse
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause

@Profile("authority-nodes")
@Service
class ThorService(private val thorRest: WebClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)

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
