package org.vechain.indexer

import org.vechain.thor.model.Block

class TestIndexer(private val mocker: IndexerResponseMocker, thorClientMock: ThorClient) :
    Indexer("0x0001", "notarealurl") {

    override val thorClient: ThorClient = thorClientMock

    override fun getLastSyncedBlockNumber(): Long {
        return mocker.getLastSyncedBlockNumber()
    }

    override fun purgeRecords(blockNumber: Long) {
        mocker.purgeRecords(blockNumber)
    }

    override fun processBlock(block: Block) {
        mocker.processBlock(block)
    }
}

interface IndexerResponseMocker {
    fun getLastSyncedBlockNumber(): Long
    fun purgeRecords(blockNumber: Long)
    fun processBlock(block: Block)
}
