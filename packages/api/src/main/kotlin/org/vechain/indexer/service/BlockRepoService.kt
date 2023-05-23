package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.utils.HexUtil

@Profile("block-indexer")
@Service
open class BlockRepoService(private val blockRepo: BlockRepo) : BlockService {

    override fun findBestBlock(): Block? {
        return blockRepo.findTopByOrderByBlockNumberDesc()
    }

    override fun findById(blockId: String): Block? {
        return blockRepo.findByIdOrNull(HexUtil.normalise(blockId))
    }

    override fun findByBlockNumber(blockNumber: Long): Block? {
        return blockRepo.findByBlockNumber(blockNumber)
    }

}