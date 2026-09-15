package org.vechain.indexer.b3tr.action

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.b3tr.action.ActionPeriod.AllTime
import org.vechain.indexer.b3tr.action.ActionPeriod.Day
import org.vechain.indexer.b3tr.action.ActionPeriod.Round
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.model.BlockRevision

class ActionSummaryServiceTest {
    private val repository: ActionWriteRepository = mockk()
    private val roundService: B3trRoundService = mockk()
    private val service = ActionSummaryService(repository, roundService, ActionImpactConfig())

    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)
    private val appX = "0x" + "11".repeat(32)
    private val appY = "0x" + "22".repeat(32)
    private val global = EntityType.GLOBAL.name

    // 2026-09-01T00:00:00Z; every block in a test lands on that day.
    private val dayStart = 1_788_220_800L
    private val day = Day("2026-09-01")

    @BeforeEach
    fun setUp() {
        every { repository.findCurrentEntities(any(), any()) } returns emptyList()
        every { repository.findCurrentAppUsers(any(), any()) } returns emptyList()
        coEvery { roundService.getCurrentRound(any<BlockRevision>()) } returns 3
    }

    private fun blockId(block: Long) = "0x" + block.toString(16).padStart(64, '0')

    private fun reward(
        block: Long,
        user: String,
        app: String,
        amount: String = "10000000000000000000",
        proof: String? = null,
        id: String = "$block-$user-$app-$amount",
    ): IndexedEvent =
        buildIndexedEvent(
            id = id,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = dayStart + block * 10,
            eventType = "B3TR_ActionReward",
            params =
                AbiEventParameters(
                    returnValues =
                        buildMap {
                            put("appId", app)
                            put("receiver", user)
                            put("amount", amount)
                            put("distributor", "0x0")
                            proof?.let { put("proof", it) }
                        }
                ),
        )

    private fun emission(block: Long, cycle: Int): IndexedEvent =
        buildIndexedEvent(
            id = "emission-$cycle",
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = dayStart + block * 10,
            eventType = "EmissionDistributed",
            params = AbiEventParameters(returnValues = mapOf("cycle" to "$cycle")),
        )

    private fun process(vararg events: IndexedEvent) = runBlocking {
        service.processEvents(events.toList())
    }

    private fun ActionSummaryUpdate.entity(
        period: ActionPeriod,
        type: EntityType,
        entity: String,
        block: Long,
    ) = entities.single {
        it.period == period &&
            it.entityType == type &&
            it.entity == entity &&
            it.blockNumber == block
    }

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected, was $actual")

    @Test
    fun `one block rolls its rewards into every period and counts the wallets it meets`() {
        val impact = """{"version":2,"description":"d","impact":{"carbon":100}}"""

        val update =
            process(
                reward(1, alice, appX, proof = impact),
                reward(1, bob, appX, amount = "5000000000000000000"),
            )

        // alice, bob, X and GLOBAL, over all time, the day and round 3.
        assertEquals(12, update.entities.size)
        assertEquals(6, update.appUsers.size)
        assertEquals(setOf(AllTime, day, Round(3)), update.entities.map { it.period }.toSet())

        val globalRow = update.entity(AllTime, EntityType.GLOBAL, global, 1)
        assertEquals(2L, globalRow.actionsRewarded)
        assertAmount("15", globalRow.totalRewardAmount)
        assertEquals(2L, globalRow.uniqueUsers)
        assertEquals(Impact(carbon = 100), globalRow.totalImpact)
        assertEquals(2L, update.entity(day, EntityType.APP, appX, 1).uniqueUsers)
        assertEquals(0L, update.entity(Round(3), EntityType.USER, alice, 1).uniqueUsers)
        assertNull(update.entity(AllTime, EntityType.USER, bob, 1).totalImpact)

        val aliceOnX = update.appUsers.single { it.period == Round(3) && it.user == alice }
        assertEquals(appX, aliceOnX.appId)
        assertAmount("10", aliceOnX.totalRewardAmount)
        assertEquals(Impact(carbon = 100), aliceOnX.totalImpact)
    }

    @Test
    fun `a stored row is built on, and a wallet already met is not counted again`() {
        every { repository.findCurrentEntities(AllTime, any()) } returns
            listOf(
                stored(
                    EntityType.USER,
                    alice,
                    actions = 5,
                    amount = "100",
                    impact = Impact(water = 7),
                ),
                stored(EntityType.GLOBAL, global, actions = 50, amount = "1000", uniqueUsers = 9),
            )
        every { repository.findCurrentAppUsers(AllTime, any()) } returns
            listOf(
                AppUserActionSummary(
                    appX,
                    alice,
                    AllTime,
                    blockId(0),
                    0,
                    0,
                    5,
                    BigDecimal("100"),
                    null,
                )
            )

        val update = process(reward(1, alice, appX))

        val aliceRow = update.entity(AllTime, EntityType.USER, alice, 1)
        assertEquals(6L, aliceRow.actionsRewarded)
        assertAmount("110", aliceRow.totalRewardAmount)
        assertEquals(Impact(water = 7), aliceRow.totalImpact)
        assertEquals(9L, update.entity(AllTime, EntityType.GLOBAL, global, 1).uniqueUsers)
        // X has never been stored, but alice's row on it has, so she is not new to X either.
        assertEquals(0L, update.entity(AllTime, EntityType.APP, appX, 1).uniqueUsers)
        assertEquals(6L, update.appUsers.single { it.period == AllTime }.actionsRewarded)
        // The day and the round hold nothing yet, so there she is new.
        assertEquals(1L, update.entity(day, EntityType.GLOBAL, global, 1).uniqueUsers)
        assertEquals(1L, update.entity(Round(3), EntityType.APP, appX, 1).uniqueUsers)
    }

    @Test
    fun `a wallet rewarded in two blocks of one entry chains within it`() {
        val update =
            process(reward(1, alice, appX), reward(2, alice, appY, amount = "5000000000000000000"))

        assertEquals(1L, update.entity(AllTime, EntityType.USER, alice, 1).actionsRewarded)
        val second = update.entity(AllTime, EntityType.USER, alice, 2)
        assertEquals(2L, second.actionsRewarded)
        assertAmount("15", second.totalRewardAmount)
        assertEquals(1L, update.entity(AllTime, EntityType.GLOBAL, global, 2).uniqueUsers)
        assertEquals(1L, update.entity(AllTime, EntityType.APP, appY, 2).uniqueUsers)
    }

    @Test
    fun `an emission moves the rewards after it into the next round, in block order`() {
        val update =
            process(
                reward(1, alice, appX),
                reward(2, bob, appX, id = "before"),
                emission(2, 4),
                reward(2, alice, appX, id = "after"),
                reward(3, bob, appY),
            )

        assertEquals(
            setOf(3, 4),
            update.entities
                .filter { it.period is Round }
                .map { (it.period as Round).roundId }
                .toSet(),
        )
        assertEquals(1L, update.entity(Round(3), EntityType.USER, bob, 2).actionsRewarded)
        assertEquals(1L, update.entity(Round(4), EntityType.USER, alice, 2).actionsRewarded)
        assertEquals(1L, update.entity(Round(4), EntityType.GLOBAL, global, 2).uniqueUsers)
        assertEquals(2L, update.entity(Round(4), EntityType.GLOBAL, global, 3).uniqueUsers)
        // The day does not care about rounds: block 2 carries both rewards.
        assertEquals(3L, update.entity(day, EntityType.GLOBAL, global, 2).actionsRewarded)

        // The round is kept for the next entry rather than asked of the contract again.
        val next = process(reward(4, alice, appX))
        assertEquals(
            Round(4),
            next.entities
                .single { it.period is Round && it.entityType == EntityType.GLOBAL }
                .period,
        )
        coVerify(exactly = 1) { roundService.getCurrentRound(any<BlockRevision>()) }
    }

    @Test
    fun `an entry of emissions alone advances the round and adds nothing`() {
        assertTrue(process(emission(1, 4), emission(2, 5)).isEmpty())
        assertEquals(
            Round(5),
            process(reward(3, alice, appX)).entities.first { it.period is Round }.period,
        )
    }

    @Test
    fun `the round is asked of the block before the first event, and again after a rollback`() {
        process(reward(7, alice, appX))
        coVerify(exactly = 1) { roundService.getCurrentRound(BlockRevision.Number(6)) }

        service.invalidateRuntimeState()
        process(reward(8, alice, appX))
        coVerify(exactly = 1) { roundService.getCurrentRound(BlockRevision.Number(7)) }
    }

    @Test
    fun `a round that skips ahead or has not started is refused`() {
        assertThrows(IllegalStateException::class.java) { process(emission(1, 5)) }

        coEvery { roundService.getCurrentRound(any<BlockRevision>()) } returns null
        service.invalidateRuntimeState()
        assertThrows(IllegalStateException::class.java) { process(reward(1, alice, appX)) }
        assertThrows(IllegalStateException::class.java) {
            process(buildIndexedEvent(eventType = "Transfer", blockNumber = 1))
        }
    }

    @Test
    fun `an impact past its threshold is left out of the totals`() {
        val tooMuch = """{"version":2,"impact":{"carbon":1000000}}"""
        val fine = """{"version":2,"impact":{"carbon":10}}"""

        val update =
            process(reward(1, alice, appX, proof = tooMuch), reward(1, bob, appX, proof = fine))

        assertEquals(
            Impact(carbon = 10),
            update.entity(AllTime, EntityType.GLOBAL, global, 1).totalImpact,
        )
        assertNull(update.entity(AllTime, EntityType.USER, alice, 1).totalImpact)
    }

    @Test
    fun `keys are normalised the way the schema reads them back`() {
        val update = process(reward(1, alice.uppercase(), appX.removePrefix("0x")))

        assertEquals(alice, update.entity(AllTime, EntityType.USER, alice, 1).entity)
        assertEquals(appX, update.appUsers.first().appId)
    }

    private fun stored(
        type: EntityType,
        entity: String,
        actions: Long,
        amount: String,
        uniqueUsers: Long = 0,
        impact: Impact? = null,
    ) =
        EntityActionSummary(
            type,
            entity,
            AllTime,
            blockId(0),
            0,
            0,
            actions,
            BigDecimal(amount),
            impact,
            uniqueUsers,
        )
}
