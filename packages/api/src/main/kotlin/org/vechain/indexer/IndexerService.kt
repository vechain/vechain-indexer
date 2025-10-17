package org.vechain.indexer

interface IndexerService {
    fun getLatestIndexedBlocks(): Map<String, Long>
}
