package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.timeseries.TimeFramePeriod

/** Both tables of the schema; the shared series paging is covered by the VET-delegated test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VthoClaimedRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: VthoClaimedWriteRepository
    private lateinit var reader: VthoClaimedReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = VthoClaimedWriteRepository(database.jdbc)
        reader = VthoClaimedReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Alice claims on tokens 1 and 2 at blocks 10 and 20; Bob claims on token 3 at block 20. */
    private fun seed() {
        writer.save(
            listOf(series(10, 100), series(20, 300, listOf(TimeFrame.HOUR))),
            listOf(
                token(alice, "1", 10, legacy = 4, delegation = 6),
                token(alice, "2", 10, legacy = 0, delegation = 90),
                token(alice, "1", 20, legacy = 4, delegation = 106),
                token(bob, "3", 20, legacy = 1, delegation = 99),
            ),
        )
    }

    private fun series(block: Long, total: Long, frames: List<TimeFrame> = emptyList()) =
        VthoClaimedByBlock(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            total = BigInteger.valueOf(total),
            legacyRewards = BigInteger.valueOf(total / 10),
            period =
                TimeFramePeriod(
                    hourOfDay = 12,
                    dayOfMonth = 25,
                    weekOfYear = 43,
                    month = 10,
                    year = 2025,
                    timeFrames = frames,
                    blockTotal = BigInteger.ONE,
                    hourTotal = BigInteger.TWO,
                    dayTotal = BigInteger.valueOf(3),
                    weekTotal = BigInteger.valueOf(4),
                    monthTotal = BigInteger.valueOf(5),
                    yearTotal = BigInteger.valueOf(6),
                ),
        )

    private fun token(
        account: String,
        tokenId: String,
        block: Long,
        legacy: Long,
        delegation: Long,
    ) =
        VthoClaimedByToken(
            account = account,
            tokenId = tokenId,
            legacyRewards = BigInteger.valueOf(legacy),
            delegationRewards = BigInteger.valueOf(delegation),
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )

    @Test
    fun `the series round-trips and the account totals sum the current row of each token`() {
        assertEquals(series(20, 300, listOf(TimeFrame.HOUR)), reader.getLatestRecord())
        assertEquals(
            VthoClaimedTotals(BigInteger.valueOf(4), BigInteger.valueOf(196)),
            reader.findByAccount(alice),
        )
        assertEquals(
            VthoClaimedTotals(BigInteger.ZERO, BigInteger.valueOf(90)),
            reader.findByAccount(alice, "2"),
        )
        assertEquals(BigInteger.valueOf(100), reader.findByAccount(bob)?.total)
        assertNull(reader.findByAccount("0x" + "c".repeat(40)))
        assertNull(reader.findByAccount(alice, "3"))
    }

    @Test
    fun `the indexer reads the current row of every token of the accounts it was given`() {
        assertEquals(
            listOf(token(alice, "1", 20, 4, 106), token(alice, "2", 10, 0, 90)),
            writer.findCurrentByAccounts(setOf(alice)).sortedBy { it.tokenId },
        )
        assertEquals(20L, writer.latest()?.blockNumber)
    }

    @Test
    fun `rollback reopens the superseded rows and truncate empties both tables`() {
        writer.rollbackFrom(20)
        assertEquals(10L, writer.latest()?.blockNumber)
        assertEquals(BigInteger.valueOf(100), reader.findByAccount(alice)?.total)
        assertNull(reader.findByAccount(bob))

        writer.truncate()
        assertNull(writer.latest())
        assertNull(reader.findByAccount(alice))
        seed()
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(10))
        assertEquals(1, writer.prune(21))
        assertEquals(BigInteger.valueOf(196), reader.findByAccount(alice)?.delegationRewards)
        writer.truncate()
        seed()
    }
}
