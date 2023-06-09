package org.vechain.indexer

import org.vechain.thor.model.Block

class TestIndexer(private val mocker: IndexerResponseMocker) : Indexer("0x0001") {

    override fun getBlockFromChain(blockNumber: Long): Block {
        return mocker.getBlockFromChain(blockNumber)
    }

    override fun getBestBlockFromChain(): Block {
        return mocker.getLatestBlockFromChain()
    }

    override fun getLastSyncedBlock(): Block {
        return mocker.getLastSyncedBlock()
    }

    override fun purgeRecords(blockNumber: Long) {
        mocker.purgeRecords(blockNumber)
    }

    override fun processBlock(block: Block) {
        mocker.processBlock(block)
    }
}

interface IndexerResponseMocker {
    fun getBlockFromChain(blockNumber: Long): Block
    fun getLatestBlockFromChain(): Block
    fun getLastSyncedBlock(): Block
    fun purgeRecords(blockNumber: Long)
    fun processBlock(block: Block)
}