package org.vechain.indexer.mocks

import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerProcessor
import org.vechain.indexer.Pruner
import org.vechain.indexer.Status
import org.vechain.indexer.event.CombinedEventProcessor
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

class TestableBlockIndexer(
    name: String,
    thorClient: ThorClient,
    processor: IndexerProcessor,
    override var status: Status = Status.SYNCING,
    eventProcessor: CombinedEventProcessor? = null,
    startBlock: Long = 0L,
    pruner: Pruner? = null,
    prunerInterval: Long = 1L,
    syncLoggerInterval: Long = 100L,
) :
    BlockIndexer(
        name = name,
        thorClient = thorClient,
        processor = processor,
        eventProcessor = eventProcessor,
        startBlock = startBlock,
        pruner = pruner,
        prunerInterval = prunerInterval,
        syncLoggerInterval = syncLoggerInterval,
        dependsOn = emptySet(),
    ) {
    var iterations: Long? = null

    suspend fun start(iterations: Long) {
        this.iterations = iterations
        super.start()
    }

    override suspend fun run() {
        val max = iterations
        var count = 0L
        while (max == null || count < max) {
            runOnce()
            count++
        }
    }

    fun publicRunPruner() {
        super.runPruner()
    }

    fun incrementBlockNumber() {
        currentBlockNumber += 1
    }

    fun publicInitialise(blockNumber: Long? = null) {
        super.initialise(blockNumber)
    }

    fun readPreviousBlock(): BlockIdentifier? = previousBlock

    fun publicRestart() {
        super.restart()
    }

    fun overwriteCurrentBlockNumber(value: Long) {
        currentBlockNumber = value
    }

    suspend fun publicPostProcessBlock(block: Block) {
        super.postProcessBlock(block)
    }

    suspend fun publicEnsureFullySynced() {
        super.ensureFullySynced()
    }
}
