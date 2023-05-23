package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.model.Block
import org.vechain.indexer.utils.HexUtil

@Profile("blocks-proxy")
@Service
open class BlockProxyService(private val thorService: ThorService) : BlockService {

    override fun findBestBlock(): Block? {
        try {
            return thorService.getBestBlock()
        } catch (e: BlockNotFoundException) {
            return null
        }
    }

    override fun findById(blockId: String): Block? {
        try {
            return thorService.getBlock(HexUtil.normalise(blockId))
        } catch (e: BlockNotFoundException) {
            return null
        }
    }

    override fun findByBlockNumber(blockNumber: Long): Block? {
        try {
            return thorService.getBlock(blockNumber)
        } catch (e: BlockNotFoundException) {
            return null
        }
    }

}
