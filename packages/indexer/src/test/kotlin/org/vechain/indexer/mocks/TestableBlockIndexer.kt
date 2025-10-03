package org.vechain.indexer.mocks

import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerProcessor
import org.vechain.indexer.Pruner
import org.vechain.indexer.event.CombinedEventProcessor
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockIdentifier

class TestableBlockIndexer(
    name: String,
    thorClient: ThorClient,
    processor: IndexerProcessor,
    eventProcessor: CombinedEventProcessor? = null,
    startBlock: Long = 0L,
    pruner: Pruner? = null,
    prunerInterval: Long = 1L,
) :
    BlockIndexer(
        name = name,
        thorClient = thorClient,
        processor = processor,
        eventProcessor = eventProcessor,
        startBlock = startBlock,
        syncLoggerInterval = 1L,
        pruner = pruner,
        prunerInterval = prunerInterval,
        inspectionClauses = null,
        dependsOn = null,
    ) {
    var iterations: Long? = null

    fun publicRunPruner() {
        super.runPruner()
    }

    fun incrementBlockNumber() {
        setCurrentBlockNumber(getCurrentBlockNumber() + 1)
    }

    fun readPreviousBlock(): BlockIdentifier? = getPreviousBlock()

    fun publicRestart() {
        super.restart()
    }

    fun overwriteCurrentBlockNumber(value: Long) {
        setCurrentBlockNumber(value)
    }
}
