package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedBlock
import org.vechain.indexer.repository.BlockRepo
import org.vechain.indexer.utils.HexUtils

@Profile("blocks")
@Service
open class BlockRepoService(private val blockRepo: BlockRepo) : BlockService {

    override fun findBestBlock(): IndexedBlock? {
        return blockRepo.findTopByOrderByBlockNumberDesc()
    }

    override fun findFinalizedBlock(): IndexedBlock? {
        return blockRepo.findTopByIsFinalizedOrderByBlockNumberDesc(true)
    }

    override fun findById(blockId: String): IndexedBlock? {
        return blockRepo.findByIdOrNull(HexUtils.normalise(blockId))
    }

    override fun findByBlockNumber(blockNumber: Long): IndexedBlock? {
        return blockRepo.findByBlockNumber(blockNumber)
    }

}
