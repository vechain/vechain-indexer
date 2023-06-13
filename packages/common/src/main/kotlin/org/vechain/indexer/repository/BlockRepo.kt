package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedBlock

@Profile("blocks")
@Repository
interface BlockRepo : BaseIndexedRepo<IndexedBlock> {
    fun findByBlockNumber(blockNumber: Long): IndexedBlock?
    fun findTopByOrderByBlockNumberDesc(): IndexedBlock?

    fun findTopByIsFinalizedOrderByBlockNumberDesc(finalized: Boolean): IndexedBlock?

    fun findTopByIsFinalizedOrderByBlockNumberAsc(finalized: Boolean): IndexedBlock?
}
