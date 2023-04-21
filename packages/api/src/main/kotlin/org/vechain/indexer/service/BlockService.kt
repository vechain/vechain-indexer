package org.vechain.indexer.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BlockRepo

@Service
open class BlockService(private val blockRepo: BlockRepo) {

    fun findBlock(revision: String): Block? {
        return if (revision == "best") {
            blockRepo.findTopByOrderByBlockNumberDesc()
        } else if (revision.startsWith("0x")) {
            blockRepo.findByIdOrNull(revision)
        } else {
            blockRepo.findByBlockNumber(revision.toLong())
        }
    }

}