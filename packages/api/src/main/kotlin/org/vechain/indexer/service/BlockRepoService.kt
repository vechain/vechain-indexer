package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedBlock
import org.vechain.indexer.repository.BlockRepository
import org.vechain.indexer.utils.HexUtils

@Profile("blocks")
@Service
open class BlockRepoService(private val blockRepository: BlockRepository) : BlockService {

    override fun findBestBlock(): IndexedBlock? {
        return blockRepository.findTopByOrderByBlockNumberDesc()
    }

    override fun findFinalizedBlock(): IndexedBlock? {
        return blockRepository.findTopByIsFinalizedOrderByBlockNumberDesc(true)
    }

    override fun findById(blockId: String): IndexedBlock? {
        return blockRepository.findByIdOrNull(HexUtils.normalise(blockId))
    }

    override fun findByBlockNumber(blockNumber: Long): IndexedBlock? {
        return blockRepository.findByBlockNumber(blockNumber)
    }

}
