package org.vechain.indexer.service

interface IndexerService {
    fun getLatestIndexedBlocks(): Map<String, Long>
}
