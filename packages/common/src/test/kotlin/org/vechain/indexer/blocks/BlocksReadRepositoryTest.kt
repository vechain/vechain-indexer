package org.vechain.indexer.blocks

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.blocks.BlocksFixtures.address
import org.vechain.indexer.blocks.BlocksFixtures.block
import org.vechain.indexer.blocks.BlocksFixtures.decodedEvent
import org.vechain.indexer.blocks.BlocksFixtures.transaction
import org.vechain.indexer.blocks.BlocksFixtures.transfer
import org.vechain.indexer.blocks.BlocksReadRepository.LatestCursor
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.transaction.IndexedTransaction

/** One test per API query on a seeded chain of five blocks; see [seed] for who did what where. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlocksReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: BlocksReadRepository

    private val alice = address(10)
    private val bob = address(11)
    private val contract = address(25)
    private val other = address(26)
    private val seeded = mutableMapOf<Long, List<IndexedTransaction>>()

    @BeforeAll
    fun start() {
        database.start()
        repository = BlocksReadRepository(database.jdbc)
        seed(BlocksWriteRepository(database.jdbc))
    }

    @AfterAll fun stop() = database.close()

    private fun seed(writer: BlocksWriteRepository) {
        var totals = BlockTotals.ZERO
        for (number in 1L..5L) {
            val b = block(number)
            val txs =
                when (number) {
                    3L ->
                        listOf(
                            transaction(
                                b,
                                0,
                                origin = alice,
                                clauses = listOf(Clause(contract, "0x0", "0x")),
                            ),
                            transaction(b, 1, origin = bob, gasPayer = alice),
                            transaction(b, 2, origin = alice, reverted = true),
                        )
                    4L ->
                        listOf(
                            transaction(
                                b,
                                0,
                                origin = alice,
                                clauses =
                                    listOf(
                                        Clause(contract, "0x1", "0x"),
                                        Clause(contract, "0x2", "0x"),
                                    ),
                                events = listOf(decodedEvent()),
                                transfers = listOf(transfer()),
                            ),
                            transaction(
                                b,
                                1,
                                origin = bob,
                                clauses = listOf(Clause(other, "0x0", "0x")),
                            ),
                        )
                    5L -> emptyList()
                    else -> listOf(transaction(b, 0, origin = alice))
                }
            totals = totals.plus(txs)
            writer.insert(block(number, txs), txs, totals)
            seeded[number] = txs
        }
    }

    private fun ids(txs: List<IndexedTransaction>) = txs.map { it.id }

    @Test
    fun `blocks page newest first from the head or from an inclusive bound`() {
        val head = repository.findBlocks(null, 2)
        assertEquals(listOf(5L, 4L), head.map { it.blockNumber })
        assertEquals(block(5), head.first())
        assertEquals(ids(seeded[4]!!), head[1].transactions)

        val bounded = repository.findBlocks(3, 10)
        assertEquals(listOf(3L, 2L, 1L), bounded.map { it.blockNumber })
        assertEquals(block(3, seeded[3]!!), bounded.first())
    }

    @Test
    fun `latest walks block number down and transaction index up, continuing after a cursor`() {
        val first = repository.findLatest(null, 3)
        assertEquals(
            listOf(4L to 0L, 4L to 1L, 3L to 0L),
            first.map { it.blockNumber to it.transactionIndex },
        )

        val next = repository.findLatest(LatestCursor(3, 0), 10)
        assertEquals(
            listOf(3L to 1L, 3L to 2L, 2L to 0L, 1L to 0L),
            next.map { it.blockNumber to it.transactionIndex },
        )
        assertEquals(0, next.first().clauses.size, "latest is a collapsed read")
        assertEquals(1, next.first().clauseCount, "but still reports the clause count")
    }

    @Test
    fun `a transaction by id is fully expanded`() {
        val expected = seeded[4]!![0]

        assertEquals(expected, repository.findById(expected.id))
        assertNull(repository.findById(BlocksFixtures.hash(999)))
    }

    @Test
    fun `origin pages honour direction, offset and expansion`() {
        val desc = repository.findByOrigin(alice, false, 0, 10, DESC, expanded = true)
        assertEquals(listOf(4L, 3L, 3L, 2L, 1L), desc.map { it.blockNumber })
        assertEquals(seeded[4]!![0], desc.first())
        // Same block: id descending mirrors Mongo's _id sort.
        assertTrue(desc[1].id > desc[2].id)

        val asc = repository.findByOrigin(alice, false, 1, 2, ASC, expanded = false)
        assertEquals(listOf(2L, 3L), asc.map { it.blockNumber })
        assertTrue(asc.all { it.clauses.isEmpty() && it.clauseCount == 1 })
        assertTrue(
            asc.all { tx -> tx.outputs.all { it.events.isEmpty() && it.transfers.isEmpty() } }
        )
    }

    @Test
    fun `includeDelegated adds the transactions the address paid for, once each`() {
        val page = repository.findByOrigin(alice, true, 0, 10, DESC, expanded = false)

        assertEquals(6, page.size)
        assertEquals(listOf(4L, 3L, 3L, 3L, 2L, 1L), page.map { it.blockNumber })
        assertEquals(page.size, page.map { it.id }.toSet().size)
        assertTrue(page.any { it.origin == bob })
    }

    @Test
    fun `delegated lists what the payer did not originate`() {
        val page = repository.findDelegated(alice, 0, 10, DESC, expanded = false)

        assertEquals(listOf(seeded[3]!![1].id), page.map { it.id })
        assertTrue(repository.findDelegated(bob, 0, 10, DESC, expanded = false).isEmpty())
    }

    @Test
    fun `contract pages count a transaction once however many clauses hit it`() {
        val page = repository.findByContract(contract, 0, 10, DESC, expanded = true)

        assertEquals(listOf(seeded[4]!![0].id, seeded[3]!![0].id), page.map { it.id })
        assertEquals(seeded[4]!![0], page.first())

        val secondAsc = repository.findByContract(contract, 1, 10, ASC, expanded = false)
        assertEquals(listOf(seeded[4]!![0].id), secondAsc.map { it.id })
        assertTrue(repository.findByContract(address(99), 0, 10, DESC, expanded = false).isEmpty())
    }

    @Test
    fun `the count comes off the newest block row`() {
        val count = repository.latestTotals()!!

        assertEquals(5L, count.blockNumber)
        assertEquals(BigInteger.valueOf(7), count.totalTransactions)
        assertEquals(BigInteger.valueOf(8), count.totalClauses)
        assertEquals(BigInteger.ONE, count.totalRevertedTransactions)
        assertEquals(BigInteger.ONE, count.totalRevertedClauses)
    }
}
