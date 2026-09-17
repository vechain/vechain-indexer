package org.vechain.indexer.b3tr.treasury

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreasuryTransferRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: TreasuryTransferWriteRepository
    private lateinit var reader: TreasuryTransferReadRepository

    @BeforeAll
    fun start() {
        database.start()
        writer = TreasuryTransferWriteRepository(database.jdbc)
        reader = TreasuryTransferReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun seed() {
        writer.save(
            listOf(
                transfer("01", 10, TreasuryTransferCategory.EMISSION),
                transfer("02", 20, TreasuryTransferCategory.OUT),
                transfer("03", 30, TreasuryTransferCategory.OUT),
            )
        )
    }

    private fun transfer(id: String, block: Long, category: TreasuryTransferCategory) =
        TreasuryTransfer(
            id = id.repeat(20),
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            txId = "0x" + block.toString(16).padStart(64, 'e'),
            from = "0x" + "a".repeat(40),
            to = "0x" + "b".repeat(40),
            value = "1000000000000000000",
            category = category,
            label = "test",
            counterpartyName = if (category == TreasuryTransferCategory.OUT) "Grants" else null,
        )

    private fun find(
        category: TreasuryTransferCategory? = null,
        after: Long? = null,
        before: Long? = null,
        offset: Long = 0,
        limit: Int = 10,
        direction: Direction = Direction.DESC,
    ) = reader.find(category, after, before, offset, limit, direction).map { it.blockNumber }

    @Test
    fun `a transfer round-trips`() {
        assertEquals(transfer("03", 30, TreasuryTransferCategory.OUT), atBlock(30))
        assertEquals(transfer("01", 10, TreasuryTransferCategory.EMISSION), atBlock(10))
    }

    private fun atBlock(block: Long) =
        reader.find(null, block * 10, block * 10, 0, 1, Direction.DESC).single()

    @Test
    fun `the page filters by category and window, newest or oldest first`() {
        assertEquals(listOf(30L, 20L, 10L), find())
        assertEquals(listOf(10L, 20L, 30L), find(direction = Direction.ASC))
        assertEquals(listOf(30L, 20L), find(category = TreasuryTransferCategory.OUT))
        assertEquals(listOf(20L), find(after = 200, before = 200))
        assertEquals(listOf(20L), find(offset = 1, limit = 1))
    }

    @Test
    fun `the write path holds up with the time pages dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(TreasuryTransferIndexes.SET)
        try {
            writer.save(listOf(transfer("04", 40, TreasuryTransferCategory.GRANT)))
            assertEquals(4, database.count("b3tr_treasury.transfer"))

            writer.rollbackFrom(40)
            assertEquals(3, database.count("b3tr_treasury.transfer"))
        } finally {
            builder.build(TreasuryTransferIndexes.SET)
        }
    }

    @Test
    fun `rollback drops the blocks it undoes and truncate empties the table`() {
        writer.rollbackFrom(20)
        assertEquals(listOf(10L), find())
        assertEquals(10L, reader.latestBlockNumber())

        writer.truncate()
        assertEquals(0L, reader.latestBlockNumber())
        seed()
    }
}
