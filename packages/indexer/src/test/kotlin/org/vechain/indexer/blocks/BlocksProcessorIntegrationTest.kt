package org.vechain.indexer.blocks

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.TransactionService

/** The processor against a real schema: fixture blocks in, resume point and totals out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlocksProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var jdbc: JdbcTemplate
    private lateinit var processor: BlocksProcessor

    @BeforeAll
    fun start() {
        database.start()
        jdbc = database.jdbc
        val repository = BlocksWriteRepository(jdbc)
        val service = BlockTreeService(BlocksService(), TransactionService(), repository)
        val store =
            BlocksIndexerStore(repository, IndexerStateRepository(jdbc), CheckpointProperties())
        processor = BlocksProcessor(service, store, mockk(relaxed = true), version = 1)
        processor.bootstrap()
    }

    @AfterAll fun stop() = database.close()

    private fun renumber(block: Block, number: Long) =
        Block(
            number = number,
            id = "0x" + number.toString(16).padStart(64, '0'),
            size = block.size,
            parentID = block.parentID,
            timestamp = block.timestamp,
            gasLimit = block.gasLimit,
            beneficiary = block.beneficiary,
            gasUsed = block.gasUsed,
            totalScore = block.totalScore,
            txsRoot = block.txsRoot,
            txsFeatures = block.txsFeatures,
            stateRoot = block.stateRoot,
            receiptsRoot = block.receiptsRoot,
            com = block.com,
            signer = block.signer,
            isTrunk = block.isTrunk,
            isFinalized = block.isFinalized,
            baseFeePerGas = block.baseFeePerGas,
            transactions = block.transactions,
        )

    private fun process(
        block: Block,
        events: List<org.vechain.indexer.event.model.generic.IndexedEvent> = emptyList(),
    ) = runBlocking {
        processor.process(IndexingResult.BlockResult(block, events, emptyList(), Status.SYNCING))
    }

    private fun count(table: String) = database.count("blocks.$table")

    @Test
    fun `blocks are written in order, resumed from, and rolled back`() {
        assertNull(processor.getLastSyncedBlock())

        process(renumber(BlockFixtures.BLOCK_NO_CLAUSES, 0))
        process(renumber(BlockFixtures.BLOCK_MULTIPLE_TXS, 1))
        process(
            renumber(BlockFixtures.BLOCK_TRANSFERS, 2),
            IndexedEventsFixtures.INDEXED_EVENTS_TRANSFERS,
        )

        assertEquals(2L, processor.getLastSyncedBlock()?.number)
        val expectedTxs =
            BlockFixtures.BLOCK_MULTIPLE_TXS.transactions.size +
                BlockFixtures.BLOCK_TRANSFERS.transactions.size
        assertEquals(expectedTxs, count("transaction"))
        val totals = BlocksWriteRepository(jdbc).newestTotals()!!
        assertEquals(expectedTxs.toLong(), totals.totalTransactions)
        val decodedEvents =
            jdbc.queryForObject(
                "SELECT count(*) FROM blocks.event WHERE name IS NOT NULL",
                Int::class.java,
            )!!
        assertEquals(true, decodedEvents > 0, "decoded events should carry their names")

        processor.rollback(1)

        assertEquals(0L, processor.getLastSyncedBlock()?.number)
        assertEquals(1, IndexerStateRepository(jdbc).storedVersion("blocks"))
        assertEquals(0, count("transaction") + count("event") + count("transfer"))
        assertEquals(BlockTotals.ZERO, BlocksWriteRepository(jdbc).newestTotals())

        // Indexing resumes after the rollback with totals taken from the surviving head row.
        process(renumber(BlockFixtures.BLOCK_MULTIPLE_TXS, 1))
        assertEquals(
            BlockFixtures.BLOCK_MULTIPLE_TXS.transactions.size.toLong(),
            BlocksWriteRepository(jdbc).newestTotals()!!.totalTransactions,
        )
    }
}
