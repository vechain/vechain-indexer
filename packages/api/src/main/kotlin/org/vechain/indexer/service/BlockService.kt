package org.vechain.indexer.service

import org.vechain.indexer.model.IndexedBlock

interface BlockService {

    fun findBestBlock(): IndexedBlock?

    fun findFinalizedBlock(): IndexedBlock?

    fun findById(blockId: String): IndexedBlock?

    fun findByBlockNumber(blockNumber: Long): IndexedBlock?

}