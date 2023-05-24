package org.vechain.indexer.service

import org.vechain.indexer.model.Block

interface BlockService {

    fun findBestBlock(): Block?

    fun findById(blockId: String): Block?

    fun findByBlockNumber(blockNumber: Long): Block?

}