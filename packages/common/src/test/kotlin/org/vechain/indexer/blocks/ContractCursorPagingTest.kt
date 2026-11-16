package org.vechain.indexer.blocks

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.blocks.BlocksFixtures.address
import org.vechain.indexer.blocks.BlocksFixtures.block
import org.vechain.indexer.blocks.BlocksFixtures.transaction
import org.vechain.indexer.blocks.BlocksReadRepository.ContractCursor
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.transaction.IndexedTransaction

/** Several contract calls per block, so the cursor has to break ties on the transaction id. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractCursorPagingTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: BlocksReadRepository

    private val contract = address(25)
    private val other = address(26)
    private val calls = mutableListOf<IndexedTransaction>()

    @BeforeAll
    fun start() {
        database.start()
        repository = BlocksReadRepository(database.jdbc)
        val writer = BlocksWriteRepository(database.jdbc)
        var totals = BlockTotals.ZERO
        for (number in 1L..4L) {
            val b = block(number)
            val txs =
                (0..3).map { i ->
                    val to = if (i == 3) other else contract
                    val clauses = List(if (i == 1) 2 else 1) { Clause(to, "0x$it", "0x") }
                    transaction(b, i, clauses = clauses)
                }
            totals = totals.plus(txs)
            writer.insert(block(number, txs), txs, totals)
            calls += txs.filter { tx -> tx.clauses.any { it.to == contract } }
        }
    }

    @AfterAll fun stop() = database.close()

    @Test
    fun `a cursor walk visits every call once, block then id, in both directions`() {
        val ascending = calls.sortedWith(compareBy({ it.blockNumber }, { it.id })).map { it.id }

        assertEquals(12, ascending.size)
        assertEquals(ascending, walk(ASC, size = 5))
        assertEquals(ascending.reversed(), walk(DESC, size = 5))
    }

    @Test
    fun `offset pages and the cursor walk agree`() {
        for (direction in listOf(DESC, ASC)) {
            val offset = repository.findByContract(contract, 0, 100, direction, false)
            assertEquals(offset.map { it.id }, walk(direction, size = 5), "direction $direction")
        }
    }

    @Test
    fun `fromBlock and toBlock bound the walk inclusively`() {
        val inRange = calls.filter { it.blockNumber in 2L..3L }.map { it.id }.sortedDescending()

        assertEquals(inRange, walk(DESC, size = 2, fromBlock = 2, toBlock = 3))
        assertTrue(repository.findByContract(contract, null, 5, null, 10, DESC, false).isEmpty())
    }

    private fun walk(
        direction: Direction,
        size: Int,
        fromBlock: Long? = null,
        toBlock: Long? = null,
    ): List<String> {
        val seen = mutableListOf<String>()
        var after: ContractCursor? = null
        while (true) {
            val page =
                repository.findByContract(
                    contract,
                    after,
                    fromBlock,
                    toBlock,
                    size,
                    direction,
                    false,
                )
            seen += page.map { it.id }
            if (page.size < size) return seen
            after = page.last().let { ContractCursor(it.blockNumber, it.id) }
        }
    }
}
