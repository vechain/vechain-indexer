package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NavigatorRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: NavigatorWriteRepository
    private lateinit var reader: NavigatorReadRepository

    private val navA = "0x" + "aa".repeat(20)
    private val navB = "0x" + "bb".repeat(20)
    private val navC = "0x" + "cc".repeat(20)
    private val citizen1 = "0x" + "11".repeat(20)
    private val citizen2 = "0x" + "22".repeat(20)
    private val txId = "0x" + "dd".repeat(32)

    @BeforeAll
    fun start() {
        database.start()
        writer = NavigatorWriteRepository(database.jdbc)
        reader = NavigatorReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /**
     * Three navigators register at block 10; citizen1 joins A at block 20; at block 30 B announces
     * an exit due at block 40, C is deactivated, citizen2 joins B and A's first fee is claimed.
     */
    private fun seed() {
        writer.save(
            NavigatorUpdate(
                navigators =
                    listOf(
                        navigator(navA, 10, "100"),
                        navigator(navB, 10, "50"),
                        navigator(navC, 10, "30"),
                    )
            )
        )
        writer.save(
            NavigatorUpdate(
                navigators = listOf(navigator(navA, 20, "100", citizens = 1, delegated = "10")),
                citizens = listOf(citizen(citizen1, navA, 20, "10")),
                delegationEvents = listOf(delegationEvent("01", navA, citizen1, 20)),
                fees = listOf(fee(1, 20, "5")),
            )
        )
        writer.save(block30())
    }

    private fun block30() =
        NavigatorUpdate(
            navigators =
                listOf(
                    navigator(navB, 30, "50", status = NavigatorStatus.EXITING, deadline = 40),
                    navigator(navC, 30, "30", status = NavigatorStatus.DEACTIVATED),
                ),
            citizens = listOf(citizen(citizen2, navB, 30, "20")),
            delegationEvents = listOf(delegationEvent("02", navB, citizen2, 30)),
            fees = listOf(fee(1, 30, "5", claimed = "5"), fee(2, 30, "7")),
        )

    private fun blockId(block: Long) = "0x" + block.toString(16).padStart(64, '0')

    private fun navigator(
        address: String,
        block: Long,
        stake: String,
        status: NavigatorStatus = NavigatorStatus.ACTIVE,
        citizens: Int = 0,
        delegated: String = "0",
        deadline: Long? = null,
    ) =
        Navigator(
            address = address,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            status = status,
            stake = BigDecimal(stake),
            citizenCount = citizens,
            totalDelegated = BigDecimal(delegated),
            metadataURI = "ipfs://$address",
            registeredAt = 1000,
            exitAnnouncedRound = deadline?.let { 3 },
            exitEffectiveDeadlineBlock = deadline,
            lastReportRound = null,
            lastReportURI = null,
        )

    private fun citizen(address: String, navigator: String, block: Long, amount: String) =
        NavigatorCitizen(
            address = address,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            navigator = navigator,
            amount = BigDecimal(amount),
            delegatedAt = block * 100,
            active = true,
        )

    private fun delegationEvent(id: String, navigator: String, citizen: String, block: Long) =
        NavigatorDelegationEvent(
            id = id.repeat(20),
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            txId = txId,
            navigator = navigator,
            citizen = citizen,
            eventType = "B3TR_DelegationCreated",
            amount = BigDecimal.TEN,
            delta = BigDecimal.TEN,
        )

    private fun fee(round: Int, block: Long, deposited: String, claimed: String? = null) =
        NavigatorFee(
            navigator = navA,
            roundId = round,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            totalDeposited = BigDecimal(deposited),
            claimedAmount = claimed?.let(::BigDecimal),
            claimedAt = claimed?.let { block * 100 },
            depositedAt = 2000,
            unlockRound = round + NavigatorFee.FEE_LOCK_PERIOD,
        )

    @Test
    @Order(1)
    fun `navigators list in the requested order with the address as tiebreak`() {
        val byRegistration =
            reader.findNavigators(null, NavigatorSort.REGISTERED_AT, Direction.ASC, 0, 10)
        assertEquals(listOf(navA, navB, navC), byRegistration.map { it.address })

        val byStake = reader.findNavigators(null, NavigatorSort.STAKE, Direction.DESC, 1, 10)
        assertEquals(listOf(navB, navC), byStake.map { it.address })

        val exiting =
            reader.findNavigators(
                listOf(NavigatorStatus.EXITING),
                NavigatorSort.CITIZEN_COUNT,
                Direction.DESC,
                0,
                10,
            )
        assertEquals(listOf(navB), exiting.map { it.address })
        assertEquals("40", exiting.single().exitEffectiveDeadline)
        assertEquals("3", exiting.single().exitAnnouncedRoundValue)
    }

    @Test
    @Order(1)
    fun `one navigator reads back current, an unknown one as nothing`() {
        val a = reader.findNavigator(navA)!!
        assertEquals(1, a.citizenCount)
        assertEquals(BigInteger.TEN, a.totalDelegatedValue)
        assertEquals("ipfs://$navA", a.metadataURI)
        assertNull(reader.findNavigator("0x" + "ee".repeat(20)))
    }

    @Test
    @Order(1)
    fun `the overview sums the navigators that are not deactivated`() {
        val overview = reader.overview()
        assertEquals(2L, overview.activeNavigators)
        assertEquals(BigInteger("150"), overview.totalStaked)
        assertEquals(1L, overview.totalCitizens)
        assertEquals(BigInteger.TEN, overview.totalDelegated)
    }

    @Test
    @Order(1)
    fun `citizens and delegation events filter by navigator and citizen`() {
        assertEquals(
            listOf(citizen1),
            reader.findCitizens(navA, 0, 10, Direction.DESC).map { it.address },
        )
        assertEquals(1, reader.findDelegationEvents(navA, null, 0, 10, Direction.DESC).size)
        assertEquals(1, reader.findDelegationEvents(null, citizen2, 0, 10, Direction.ASC).size)
        assertTrue(reader.findDelegationEvents(navA, citizen2, 0, 10, Direction.DESC).isEmpty())
        assertEquals(
            "01".repeat(20),
            reader.findDelegationEvents(navA, null, 0, 10, Direction.DESC).single().id,
        )
    }

    @Test
    @Order(1)
    fun `fees sum into the summaries and page by round`() {
        val global = reader.feeSummary(null)
        assertEquals(BigInteger("12"), global.totalEarned)
        assertEquals(BigInteger("5"), global.totalClaimed)
        assertEquals(global, reader.feeSummary(navA))
        assertEquals(NavigatorFeeSummary(BigInteger.ZERO, BigInteger.ZERO), reader.feeSummary(navB))

        val fees = reader.findFees(navA, 0, 10, Direction.DESC)
        assertEquals(listOf(2, 1), fees.map { it.roundId })
        assertTrue(fees[1].claimed)
        assertFalse(fees[0].claimed)
        assertEquals("${navA}_2", fees[0].id)
        assertEquals(30L, reader.latestBlockNumber())
    }

    @Test
    @Order(1)
    fun `the writer finds the exits that fall due, their citizens and the fees a block touches`() {
        assertTrue(writer.findExpiredExits(39).isEmpty())
        assertEquals(listOf(navB), writer.findExpiredExits(40).map { it.address })
        assertEquals(
            listOf(citizen2),
            writer.findActiveCitizens(setOf(navB), 40).map { it.address },
        )
        assertEquals(
            BigDecimal("5"),
            writer.findCurrentFees(setOf(navA to 1), 40).single().claimedAmount,
        )
        assertEquals(
            setOf(navA, navC),
            writer.findCurrentNavigators(setOf(navA, navC), 40).map { it.address }.toSet(),
        )
        assertEquals(
            BigDecimal.TEN,
            writer.findCurrentCitizens(setOf(citizen1), 40).single().amount,
        )
    }

    @Test
    @Order(1)
    fun `a replayed block reads the state its first run started from`() {
        val before = writer.findCurrentNavigators(setOf(navB, navC), 30)
        assertEquals(setOf(NavigatorStatus.ACTIVE), before.map { it.status }.toSet())
        assertEquals(setOf(10L), before.map { it.blockNumber }.toSet())
        assertTrue(writer.findCurrentCitizens(setOf(citizen2), 30).isEmpty())
        assertNull(writer.findCurrentFees(setOf(navA to 1), 30).single().claimedAmount)
        assertEquals(listOf(navB), writer.findExpiredExits(45).map { it.address })
        assertTrue(writer.findExpiredExits(30).isEmpty())
    }

    @Test
    @Order(1)
    fun `the exit and citizen lookups survive the delegation feed being dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(NavigatorIndexes.SET)
        try {
            assertEquals(listOf(navB), writer.findExpiredExits(40).map { it.address })
            assertEquals(
                listOf(citizen2),
                writer.findActiveCitizens(setOf(navB), 40).map { it.address },
            )
            assertEquals(
                setOf(navA, navC),
                writer.findCurrentNavigators(setOf(navA, navC), 40).map { it.address }.toSet(),
            )
            assertEquals(1, writer.findCurrentCitizens(setOf(citizen1), 40).size)
            assertEquals(1, writer.findCurrentFees(setOf(navA to 1), 40).size)
        } finally {
            builder.build(NavigatorIndexes.SET)
        }
    }

    @Test
    @Order(2)
    fun `prune drops only the rows a later block superseded before the horizon`() {
        assertEquals(1, writer.prune(21))
        assertEquals(0, writer.prune(21))
        assertEquals(3, database.count(NavigatorRowMapping.TABLE + " WHERE superseded_at IS NULL"))
    }

    @Test
    @Order(3)
    fun `rollback reopens the superseded rows and drops what the block added`() {
        writer.rollbackFrom(30)
        val overview = reader.overview()
        assertEquals(3L, overview.activeNavigators)
        assertEquals(BigInteger("180"), overview.totalStaked)
        assertFalse(reader.findFees(navA, 0, 10, Direction.ASC).single().claimed)
        assertTrue(reader.findCitizens(navB, 0, 10, Direction.ASC).isEmpty())
        assertTrue(reader.findDelegationEvents(navB, null, 0, 10, Direction.ASC).isEmpty())
        assertEquals(20L, reader.latestBlockNumber())

        // Replaying the block lands on the same rows, so the schema needs no idempotent caller.
        writer.save(block30())
        writer.save(block30())
        assertEquals(2L, reader.overview().activeNavigators)
        assertEquals(2, reader.findFees(navA, 0, 10, Direction.ASC).size)
    }

    @Test
    @Order(4)
    fun `truncate empties every table`() {
        writer.truncate()
        assertEquals(0L, reader.latestBlockNumber())
        assertEquals(0L, reader.overview().activeNavigators)
    }
}
