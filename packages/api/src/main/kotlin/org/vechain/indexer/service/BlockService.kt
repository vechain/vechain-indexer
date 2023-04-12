package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BlockRepo

@Service
open class BlockService(private val blockRepo: BlockRepo) {

    open fun findAll(pageable: Pageable): List<Block> {
        return blockRepo.findAll(pageable).toList()
    }

}