package org.vechain.indexer.accounts

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountsRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: AccountsWriteRepository
    private lateinit var overviews: AccountOverviewReadRepository
    private lateinit var totals: AccountTotalsReadRepository
    private lateinit var balances: VetBalanceReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val carol = "0x" + "c".repeat(40)
    private val dave = "0x" + "d".repeat(40)
    private val day = 1_704_067_200L

    @BeforeAll
    fun start() {
        database.start()
        writer = AccountsWriteRepository(database.jdbc)
        overviews = AccountOverviewReadRepository(database.jdbc)
        totals = AccountTotalsReadRepository(database.jdbc)
        balances = VetBalanceReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /**
     * Genesis funds alice and bob; block 10 moves VET from alice to carol; block 20 only counts.
     */
    private fun seed() {
        writer.saveGenesis(
            listOf(overview(alice, 0, vet = 1_000), overview(bob, 0, vet = 500)),
            listOf(balance(alice, 0, 1_000), balance(bob, 0, 500)),
        )
        writer.save(
            AccountsUpdate(
                blockNumber = 10,
                overviews = listOf(aliceAt10(), overview(carol, 10, vet = 100, settled = ts(10))),
                balances = listOf(balance(alice, 10, 900), balance(carol, 10, 100)),
                newAccounts = listOf(alice, carol),
                totals = totals(10, 2, listOf(TimeFrame.HOUR)),
            )
        )
        writer.save(
            AccountsUpdate(
                blockNumber = 20,
                newAccounts = listOf(bob),
                totals = totals(20, 3, listOf(TimeFrame.HOUR, TimeFrame.DAY)),
            )
        )
    }

    private fun ts(block: Long) = day + block * 3600

    private fun blockId(block: Long) = "0x" + block.toString(16).padStart(64, '0')

    private fun vet(amount: Long): BigInteger = BigInteger.valueOf(amount) * BigInteger.TEN.pow(18)

    private fun overview(address: String, block: Long, vet: Long, settled: Long? = null) =
        AccountOverview(
            address = address,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = ts(block),
            firstSeen = ts(0),
            lastSeen = ts(block),
            vetBalance = vet(vet),
            lastVthoSettlement = settled,
        )

    private fun aliceAt10() =
        overview(alice, 10, vet = 900, settled = ts(10)).apply {
            transactionsSent = 1
            clausesSent = 2
            vetSent = vet(100)
            vthoBurned = BigInteger.valueOf(21_000)
        }

    private fun balance(address: String, block: Long, vet: Long) =
        VetBalance(address, blockId(block), block, ts(block), vet(vet))

    private fun totals(block: Long, count: Long, frames: List<TimeFrame>) =
        AccountTotalsSeries(blockId(block), block, ts(block), count, frames)

    @Test
    fun `an overview round-trips and only its newest row is current`() {
        assertEquals(aliceAt10(), overviews.findByAddress(alice))
        assertEquals(0L, overviews.findByAddress(bob)?.blockNumber)
        assertNull(overviews.findByAddress(dave))
        assertEquals(
            mapOf(alice to 10L, bob to 0L, carol to 10L),
            writer.findCurrentOverviews(setOf(alice, bob, carol, dave)).associate {
                it.address to it.blockNumber
            },
        )
        assertTrue(writer.hasOverviews())

        writer.save(AccountsUpdate(10, overviews = listOf(aliceAt10())))
        assertEquals(1, writer.findCurrentOverviews(setOf(alice)).size)
        assertEquals(2, database.count("accounts.overview WHERE address = '\\x${"a".repeat(40)}'"))
    }

    @Test
    fun `balances read newest first inside the window`() {
        assertEquals(
            listOf(balance(alice, 10, 900), balance(alice, 0, 1_000)),
            balances.findByAddressBetween(alice, ts(0), ts(10)),
        )
        assertEquals(emptyList<VetBalance>(), balances.findByAddressBetween(alice, ts(1), ts(9)))
    }

    @Test
    fun `the count knows its members and samples by frame`() {
        assertEquals(setOf(alice, bob, carol), writer.findSeen(setOf(alice, bob, carol, dave)))
        assertEquals(emptySet<String>(), writer.findSeen(emptySet()))
        assertEquals(10L, writer.findTotalsBefore(20)?.blockNumber)
        assertNull(writer.findTotalsBefore(10))

        assertEquals(3L, totals.findLatest()?.totalAccounts)
        assertEquals(
            listOf(10L, 20L),
            totals.findAllInTimestampRange(ts(0), ts(20)).map { it.blockNumber },
        )
        assertEquals(
            listOf(totals(20, 3, listOf(TimeFrame.HOUR, TimeFrame.DAY))),
            totals.findFrameInTimestampRange(TimeFrame.DAY, ts(0), ts(20)),
        )
        assertEquals(10L, totals.findLatestAtOrBefore(ts(15))?.blockNumber)
        assertNull(totals.findLatestAtOrBefore(ts(9)))
    }

    @Test
    fun `the Hayabusa settlement credits every holder still generating, once`() {
        assertEquals(2, writer.settlePassiveVtho(blockId(30), 30, ts(30)))
        assertEquals(0, writer.settlePassiveVtho(blockId(30), 30, ts(30)))

        // 900 VET over 20 hours at 5e-9 VTHO per VET-second.
        val settled = overviews.findByAddress(alice)!!
        assertEquals(30L, settled.blockNumber)
        assertEquals(
            vet(900) * BigInteger.valueOf(72_000 * 5) / BigInteger.TEN.pow(9),
            settled.vthoPassiveGeneration,
        )
        assertEquals(ts(30), settled.lastVthoSettlement)
        assertEquals(ts(30), settled.lastSeen)
        assertEquals(ts(0), settled.firstSeen)
        assertEquals(1L, settled.transactionsSent)
        assertEquals(vet(900), settled.vetBalance)
        assertEquals(30L, overviews.findByAddress(carol)?.blockNumber)
        // Never settled, so never generating.
        assertEquals(0L, overviews.findByAddress(bob)?.blockNumber)

        writer.rollbackFrom(30)
        assertEquals(aliceAt10(), overviews.findByAddress(alice))
    }

    @Test
    fun `rollback undoes all four tables together and truncate empties them`() {
        writer.rollbackFrom(20)
        assertEquals(emptySet<String>(), writer.findSeen(setOf(bob)))
        assertEquals(10L, totals.findLatest()?.blockNumber)

        writer.rollbackFrom(10)
        assertEquals(overview(alice, 0, vet = 1_000), overviews.findByAddress(alice))
        assertNull(overviews.findByAddress(carol))
        assertEquals(
            listOf(0L),
            balances.findByAddressBetween(alice, 0, ts(10)).map { it.blockNumber },
        )
        assertEquals(emptySet<String>(), writer.findSeen(setOf(alice, bob, carol)))
        assertNull(totals.findLatest())

        writer.truncate()
        assertFalse(writer.hasOverviews())
        seed()
    }

    @Test
    fun `prune drops superseded overviews below the horizon and reports how many`() {
        assertEquals(0, writer.prune(10))
        assertEquals(1, writer.prune(11))
        assertEquals(aliceAt10(), overviews.findByAddress(alice))
        writer.truncate()
        seed()
    }
}
