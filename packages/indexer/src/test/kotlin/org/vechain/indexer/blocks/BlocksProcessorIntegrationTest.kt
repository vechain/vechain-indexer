package org.vechain.indexer.blocks

import com.zaxxer.hikari.HikariDataSource
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.config.postgres.PostgresProperties
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.TransactionService

/** The processor against a real schema: fixture blocks in, resume point and totals out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlocksProcessorIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16")
    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var processor: BlocksProcessor

    @BeforeAll
    fun start() {
        postgres.start()
        val config = PostgresConfig()
        dataSource =
            config.postgresDataSource(
                PostgresProperties(postgres.jdbcUrl, postgres.username, postgres.password)
            ) as HikariDataSource
        config.postgresFlyway(dataSource).migrate()
        jdbc = JdbcTemplate(dataSource)
        val repository = BlocksWriteRepository(jdbc)
        val service = BlockTreeService(BlocksService(), TransactionService(), repository, 1)
        processor = BlocksProcessor(service, repository, mockk(relaxed = true))
        processor.bootstrap()
    }

    @AfterAll
    fun stop() {
        dataSource.close()
        postgres.stop()
    }

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

    private fun count(table: String) =
        jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

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
                "SELECT count(*) FROM event WHERE name IS NOT NULL",
                Int::class.java,
            )!!
        assertEquals(true, decodedEvents > 0, "decoded events should carry their names")

        processor.rollback(1)

        assertEquals(0L, processor.getLastSyncedBlock()?.number)
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
