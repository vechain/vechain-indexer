package org.vechain.indexer.b3tr.balance

import java.math.BigDecimal
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.b3tr.balance.B3trBalanceRowMapping.TABLE as TABLE_REF
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class B3trBalanceRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: B3trBalanceWriteRepository
    private lateinit var reader: B3trBalanceReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val carol = "0x" + "c".repeat(40)
    private val spent = "0x" + "d".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = B3trBalanceWriteRepository(database.jdbc)
        reader = B3trBalanceReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Three holders and one address that has spent everything, so it is off the richlist. */
    private fun seed() {
        writer.save(
            listOf(
                balance(alice, 10, vot3 = 100, b3tr = 50),
                balance(bob, 10, vot3 = 80, b3tr = 20),
                balance(carol, 10, vot3 = 0, b3tr = 100),
                balance(spent, 10, vot3 = 1, b3tr = 0),
                balance(spent, 20, vot3 = 0, b3tr = 0),
            )
        )
    }

    private fun balance(address: String, block: Long, vot3: Long, b3tr: Long) =
        B3trBalance(
            address = address,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            vot3Balance = BigDecimal.valueOf(vot3),
            b3trBalance = BigDecimal.valueOf(b3tr),
            totalBalance = BigDecimal.valueOf(vot3 + b3tr),
        )

    private fun page(
        on: B3trBalanceColumn,
        limit: Int = 10,
        direction: Direction = Direction.DESC,
        cursorBalance: Long? = null,
        cursorAddress: String? = null,
    ) =
        reader
            .page(on, limit, direction, cursorBalance?.let(BigDecimal::valueOf), cursorAddress)
            .map { it.address }

    @Test
    fun `a balance round-trips and only its newest row is current`() {
        assertEquals(BigDecimal.valueOf(150), reader.findByAddress(alice)?.totalBalance)
        assertEquals(20L, reader.findByAddress(spent)?.blockNumber)
        assertNull(reader.findByAddress("0x" + "9".repeat(40)))
    }

    @Test
    fun `the richlist pages by one balance and leaves out the addresses holding none`() {
        assertEquals(listOf(alice, bob, carol), page(B3trBalanceColumn.TOTAL))
        assertEquals(listOf(alice, bob), page(B3trBalanceColumn.VOT3))
        assertEquals(listOf(carol, alice, bob), page(B3trBalanceColumn.B3TR))
        assertEquals(
            listOf(bob, carol, alice),
            page(B3trBalanceColumn.TOTAL, direction = Direction.ASC),
        )
    }

    @Test
    fun `a cursor resumes after the row it names, ties broken by address`() {
        assertEquals(
            listOf(bob, carol),
            page(B3trBalanceColumn.TOTAL, cursorBalance = 150, cursorAddress = alice),
        )
        assertEquals(
            listOf(alice, bob),
            page(B3trBalanceColumn.TOTAL, limit = 2, cursorBalance = 200, cursorAddress = alice),
        )
    }

    @Test
    fun `the holder counts drive the ranks`() {
        assertEquals(3L, reader.countGreaterThan(B3trBalanceColumn.TOTAL, BigDecimal.ZERO))
        assertEquals(1L, reader.countGreaterThan(B3trBalanceColumn.TOTAL, BigDecimal.valueOf(100)))
        assertEquals(2L, reader.countGreaterThan(B3trBalanceColumn.VOT3, BigDecimal.ZERO))
    }

    @Test
    fun `rollback reopens the superseded row and truncate empties the table`() {
        assertEquals(200L, reader.newestBlockTimestamp())

        writer.rollbackFrom(20)
        assertEquals(10L, reader.findByAddress(spent)?.blockNumber)
        assertEquals(4L, reader.countGreaterThan(B3trBalanceColumn.TOTAL, BigDecimal.ZERO))
        assertEquals(100L, reader.newestBlockTimestamp())

        writer.truncate()
        assertNull(reader.newestBlockTimestamp())
        seed()
    }

    /** A retried batch re-reads block 20 against the rows block 20 first started from. */
    @Test
    fun `the balance before a block ignores that block's own row and the rows it closed`() {
        assertEquals(
            listOf(10L),
            writer.findCurrentByAddresses(setOf(spent), 20).map { it.blockNumber },
        )
        assertEquals(
            BigDecimal.ONE,
            writer.findCurrentByAddresses(setOf(spent), 20).single().vot3Balance,
        )
        assertEquals(
            listOf(20L),
            writer.findCurrentByAddresses(setOf(spent), 21).map { it.blockNumber },
        )
        assertEquals(emptyList<B3trBalance>(), writer.findCurrentByAddresses(setOf(spent), 10))
        assertEquals(
            setOf(alice, bob),
            writer
                .findCurrentByAddresses(setOf(alice, bob, "0x" + "9".repeat(40)), 30)
                .map { it.address }
                .toSet(),
        )

        writer.save(listOf(balance(spent, 20, vot3 = 0, b3tr = 0)))
        assertEquals(20L, reader.findByAddress(spent)?.blockNumber)
        assertEquals(
            2,
            database.count("$TABLE_REF WHERE address = decode('${spent.drop(2)}', 'hex')"),
        )
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(20))
        assertEquals(1, writer.prune(21))
        assertEquals(20L, reader.findByAddress(spent)?.blockNumber)
        writer.truncate()
        seed()
    }
}
