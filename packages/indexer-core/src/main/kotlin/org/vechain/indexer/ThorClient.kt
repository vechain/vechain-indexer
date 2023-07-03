package org.vechain.indexer

import org.vechain.thor.model.Block

interface ThorClient {
    suspend fun getBlock(blockNumber: Long): Block

    suspend fun getBestBlock(): Block
}
