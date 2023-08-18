package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.client.ThorClient
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.IndexedBlock

@Profile("blocks-proxy")
@Service
open class BlockProxyService(private val thorClient: ThorClient) : BlockService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun findBestBlock(): IndexedBlock? {
        return try {
            val response = thorClient.getBestBlock()

            if (logger.isDebugEnabled) logger.debug("Best block found: ${response.number}")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.warn("Best block not found")
            null
        }
    }

    override fun findFinalizedBlock(): IndexedBlock? {
        return try {
            val response = thorClient.getFinalizedBlock()

            if (logger.isDebugEnabled) logger.debug("Finalized block found: ${response.number}")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.warn("Finalized block not found")
            null
        }
    }

    override fun findById(blockId: String): IndexedBlock? {
        return try {
            val response = thorClient.getBlockById(blockId)

            if (logger.isDebugEnabled) logger.debug("Block $blockId found")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.info("Block $blockId not found")
            null
        }
    }

    override fun findByBlockNumber(blockNumber: Long): IndexedBlock? {
        return try {
            val response = thorClient.getBlock(blockNumber)

            if (logger.isDebugEnabled) logger.debug("Block $blockNumber found")

            return IndexedBlock(response)
        } catch (e: NotFoundException) {
            logger.info("Block $blockNumber not found")
            null
        }
    }
}
