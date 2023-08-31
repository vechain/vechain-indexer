package org.vechain.indexer

import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

class IndexerMock(private val mocker: IndexerResponseMocker, thorClientMock: ThorClient) :
    Indexer(DefaultThorClient("notarealurl"), 0L) {

    override val thorClient: ThorClient = thorClientMock

    override fun getLastSyncedBlockNumber(): Long {
        return mocker.getLastSyncedBlockNumber()
    }

    override fun rollback(blockNumber: Long) {
        mocker.rollback(blockNumber)
    }

    override fun processBlock(block: Block) {
        mocker.processBlock(block)
    }
}

interface IndexerResponseMocker {
    fun getLastSyncedBlockNumber(): Long

    fun rollback(blockNumber: Long)

    fun processBlock(block: Block)
}
