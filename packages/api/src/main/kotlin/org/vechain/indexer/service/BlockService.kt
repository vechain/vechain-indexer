package org.vechain.indexer.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class BlockService(private val blockRepo: BlockRepo) {

    fun findBestBlock(): Block? {
        return blockRepo.findTopByOrderByBlockNumberDesc()
    }

    fun findById(blockId: String): Block? {
        return blockRepo.findByIdOrNull(HexUtil.normalise(blockId))
    }

    fun findByBlockNumber(blockNumber: Long): Block? {
        return blockRepo.findByBlockNumber(blockNumber)
    }

}