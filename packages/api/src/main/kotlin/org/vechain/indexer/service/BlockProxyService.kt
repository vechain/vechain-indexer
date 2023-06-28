package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.IndexedBlock
import org.vechain.thor.model.Block

@Profile("blocks-proxy")
@Service
open class BlockProxyService(private val thorRest: WebClient) : BlockService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun findBestBlock(): IndexedBlock? {
        return try {
            val response =
                thorRest
                    .get()
                    .uri("/blocks/best?expanded=true")
                    .retrieve()
                    .bodyToMono(Block::class.java)
                    .block()
                    ?: throw NotFoundException("Best block not found")

            if (logger.isDebugEnabled) logger.debug("Best block found: ${response.number}")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.warn("Best block not found")
            null
        }
    }

    override fun findFinalizedBlock(): IndexedBlock? {
        return try {
            val response =
                thorRest
                    .get()
                    .uri("/blocks/finalized?expanded=true")
                    .retrieve()
                    .bodyToMono(Block::class.java)
                    .block()
                    ?: throw NotFoundException("Finalized block not found")

            if (logger.isDebugEnabled) logger.debug("Finalized block found: ${response.number}")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.warn("Finalized block not found")
            null
        }
    }

    override fun findById(blockId: String): IndexedBlock? {
        return try {
            val response =
                thorRest
                    .get()
                    .uri("/blocks/$blockId?expanded=true")
                    .retrieve()
                    .bodyToMono(Block::class.java)
                    .block()
                    ?: throw NotFoundException("Block ${blockId} not found")

            if (logger.isDebugEnabled) logger.debug("Block $blockId found")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.info("Block $blockId not found")
            null
        }
    }

    override fun findByBlockNumber(blockNumber: Long): IndexedBlock? {
        return try {
            val response =
                thorRest
                    .get()
                    .uri("/blocks/$blockNumber?expanded=true")
                    .retrieve()
                    .bodyToMono(Block::class.java)
                    .block()
                    ?: throw NotFoundException("Block ${blockNumber} not found")

            if (logger.isDebugEnabled) logger.debug("Block $blockNumber found")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.info("Block $blockNumber not found")
            null
        }
    }
}
