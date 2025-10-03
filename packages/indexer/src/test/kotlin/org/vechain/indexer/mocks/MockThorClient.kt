package org.vechain.indexer.mocks

import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.*

class MockThorClient(private val blocks: Map<Long, Block>) : ThorClient {
    override suspend fun getBlock(blockNumber: Long): Block =
        blocks[blockNumber] ?: error("No block for number $blockNumber")

    override suspend fun waitForBlock(blockNumber: Long): Block {
        return getBlock(blockNumber)
    }

    override suspend fun getBestBlock(): Block = blocks.values.maxBy { it.number }

    override suspend fun getFinalizedBlock(): Block = getBestBlock()

    override suspend fun getEventLogs(req: EventLogsRequest): List<EventLog> = listOf()

    override suspend fun getVetTransfers(req: TransferLogsRequest): List<TransferLog> = listOf()

    override suspend fun inspectClauses(
        clauses: List<Clause>,
        blockID: String,
    ): List<InspectionResult> = listOf()
}
