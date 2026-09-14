package org.vechain.indexer.stargate.staking

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.timeseries.TimeFramePeriod

/** All three tables of the schema; series paging is covered by the VET-delegated test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StargateStakingRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: StargateStakingWriteRepository
    private lateinit var vetStaked: VetStakedReadRepository
    private lateinit var nftHolders: NftHoldersReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = StargateStakingWriteRepository(database.jdbc)
        vetStaked = VetStakedReadRepository(database.jdbc)
        nftHolders = NftHoldersReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Alice stakes at blocks 10 and 20, Bob at 20; the HOUR frame closes at block 20. */
    private fun seed() {
        writer.save(
            listOf(staked(10, 100), staked(20, 300, listOf(TimeFrame.HOUR))),
            listOf(holders(10, 1), holders(20, 2, listOf(TimeFrame.HOUR))),
            listOf(balance(alice, 10, 1), balance(alice, 20, 2), balance(bob, 20, 1)),
        )
    }

    private fun staked(block: Long, total: Long, frames: List<TimeFrame> = emptyList()) =
        VetStakedByBlock(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            total = BigInteger.TEN.pow(24) + BigInteger.valueOf(total),
            byLevel =
                mapOf(
                    TokenLevel.Dawn to BigInteger.TEN.pow(24),
                    TokenLevel.Strength to BigInteger.valueOf(total),
                ),
            totalNftCount = 2,
            nftCountByLevel = mapOf(TokenLevel.Dawn to 1L, TokenLevel.Strength to 1L),
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

    private fun holders(block: Long, total: Long, frames: List<TimeFrame> = emptyList()) =
        NftHoldersByBlock(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            total = total,
            byLevel = mapOf(TokenLevel.Strength to total, TokenLevel.Thunder to 0L),
            period =
                TimeFramePeriod(
                    hourOfDay = 12,
                    dayOfMonth = 25,
                    weekOfYear = 43,
                    month = 10,
                    year = 2025,
                    timeFrames = frames,
                    blockTotal = BigInteger.valueOf(-1),
                    hourTotal = BigInteger.ZERO,
                ),
        )

    private fun balance(owner: String, block: Long, total: Long) =
        NftOwnerBalance(
            owner = owner,
            total = total,
            byLevel = mapOf(TokenLevel.Strength to total),
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )

    @Test
    fun `both series round-trip through their readers and the writer`() {
        assertEquals(staked(20, 300, listOf(TimeFrame.HOUR)), vetStaked.getLatestRecord())
        assertEquals(holders(20, 2, listOf(TimeFrame.HOUR)), nftHolders.getLatestRecord())
        assertEquals(staked(10, 100), vetStaked.findLatestBeforeOrAtBlockNumber(15))
        assertEquals(
            listOf(20L),
            nftHolders.findByTimeFramesContainsAndBlockTimestampAfter(TimeFrame.HOUR, 0).map {
                it.blockNumber
            },
        )
        assertEquals(20L, writer.latestVetStaked()?.blockNumber)
        assertEquals(20L, writer.latestNftHolders()?.blockNumber)
    }

    @Test
    fun `the balance before a block is each owner's newest row below it`() {
        assertEquals(
            listOf(balance(alice, 20, 2), balance(bob, 20, 1)),
            writer.latestBalancesBefore(setOf(alice, bob), 21).sortedBy { it.owner },
        )
        assertEquals(
            listOf(balance(alice, 10, 1)),
            writer.latestBalancesBefore(setOf(alice, bob), 20),
        )
        assertEquals(emptyList<NftOwnerBalance>(), writer.latestBalancesBefore(setOf(bob), 20))
    }

    @Test
    fun `rollback trims all three tables by block and truncate empties them`() {
        writer.rollbackFrom(20)
        assertEquals(10L, writer.latestVetStaked()?.blockNumber)
        assertEquals(10L, writer.latestNftHolders()?.blockNumber)
        assertEquals(
            listOf(balance(alice, 10, 1)),
            writer.latestBalancesBefore(setOf(alice, bob), 99),
        )

        writer.truncate()
        assertNull(writer.latestVetStaked())
        assertNull(nftHolders.getLatestRecord())
        assertEquals(emptyList<NftOwnerBalance>(), writer.latestBalancesBefore(setOf(alice), 99))
        seed()
    }
}
