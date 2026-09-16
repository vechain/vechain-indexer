package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.b3tr.action.ActionPeriod.AllTime
import org.vechain.indexer.b3tr.action.ActionPeriod.Day
import org.vechain.indexer.b3tr.action.ActionPeriod.Round
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActionRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ActionWriteRepository
    private lateinit var reader: ActionReadRepository

    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)
    private val carol = "0x" + "cc".repeat(20)
    private val appX = "0x" + "11".repeat(32)
    private val appY = "0x" + "22".repeat(32)
    private val global = EntityType.GLOBAL.name
    private val periods = listOf(AllTime, Day("2026-09-01"), Round(3))
    private val impact = Impact(carbon = 100, water = 5)

    @BeforeAll
    fun start() {
        database.start()
        writer = ActionWriteRepository(database.jdbc)
        reader = ActionReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /**
     * Block 10: alice, bob and carol are rewarded on X. Block 20: alice is rewarded on Y, so her
     * rows and the GLOBAL ones move on while the others stay. Alice also has an earlier day and the
     * GLOBAL row an earlier round.
     */
    private fun seed() {
        writer.save(
            ActionSummaryUpdate(
                entities =
                    periods.flatMap { p ->
                        listOf(
                            entity(EntityType.USER, alice, p, 10, 1, "10", impact = impact),
                            entity(EntityType.USER, bob, p, 10, 1, "5"),
                            entity(EntityType.USER, carol, p, 10, 1, "3"),
                            entity(EntityType.APP, appX, p, 10, 3, "18", uniqueUsers = 3),
                            entity(EntityType.GLOBAL, global, p, 10, 3, "18", uniqueUsers = 3),
                            entity(EntityType.USER, alice, p, 20, 2, "11", impact = impact),
                            entity(EntityType.APP, appY, p, 20, 1, "1", uniqueUsers = 1),
                            entity(EntityType.GLOBAL, global, p, 20, 4, "19", uniqueUsers = 3),
                        )
                    } +
                        entity(EntityType.USER, alice, Day("2026-08-31"), 5, 1, "2") +
                        entity(EntityType.GLOBAL, global, Round(2), 5, 1, "2", uniqueUsers = 1),
                appUsers =
                    periods.flatMap { p ->
                        listOf(
                            appUser(appX, alice, p, 10, 1, "10"),
                            appUser(appX, bob, p, 10, 1, "5"),
                            appUser(appX, carol, p, 10, 1, "3"),
                            appUser(appY, alice, p, 20, 1, "1"),
                        )
                    },
            )
        )
    }

    private fun blockId(block: Long) = "0x" + block.toString(16).padStart(64, '0')

    private fun entity(
        type: EntityType,
        entity: String,
        period: ActionPeriod,
        block: Long,
        actions: Long,
        amount: String,
        uniqueUsers: Long = 0,
        impact: Impact? = null,
    ) =
        EntityActionSummary(
            entityType = type,
            entity = entity,
            period = period,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            actionsRewarded = actions,
            totalRewardAmount = BigDecimal(amount),
            totalImpact = impact,
            uniqueUsers = uniqueUsers,
        )

    private fun appUser(
        appId: String,
        user: String,
        period: ActionPeriod,
        block: Long,
        actions: Long,
        amount: String,
    ) =
        AppUserActionSummary(
            appId = appId,
            user = user,
            period = period,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            actionsRewarded = actions,
            totalRewardAmount = BigDecimal(amount),
            totalImpact = null,
        )

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected, was $actual")

    @Test
    fun `an entity round-trips with its impact and the wallets it counts`() {
        val aliceAllTime = reader.findEntity(AllTime, EntityType.USER, alice)!!
        assertEquals(20L, aliceAllTime.blockNumber)
        assertEquals(2L, aliceAllTime.actionsRewarded)
        assertAmount("11", aliceAllTime.totalRewardAmount)
        assertEquals(impact, aliceAllTime.totalImpact)
        assertEquals(AllTime, aliceAllTime.period)

        assertEquals(3L, reader.findEntity(AllTime, EntityType.APP, appX)!!.uniqueUsers)
        val globalRound = reader.findEntity(Round(3), EntityType.GLOBAL, global)!!
        assertEquals(3L, globalRound.uniqueUsers)
        assertEquals(4L, globalRound.actionsRewarded)
        assertEquals(global, globalRound.entity)
        assertEquals(Round(3), globalRound.period)
        assertEquals(
            Day("2026-09-01"),
            reader.findEntity(Day("2026-09-01"), EntityType.USER, bob)!!.period,
        )
        assertNull(reader.findEntity(Round(4), EntityType.USER, alice))

        // Keys compare as bytes, so the case and the prefix a caller uses do not matter.
        assertEquals(
            aliceAllTime,
            reader.findEntity(AllTime, EntityType.USER, alice.uppercase().removePrefix("0X")),
        )
    }

    @Test
    fun `a wallet's apps and its rows on them`() {
        assertEquals(listOf(appX, appY), reader.findAppIds(AllTime, alice))
        assertEquals(listOf(appX), reader.findAppIds(Round(3), bob))
        assertEquals(emptyList<String>(), reader.findAppIds(Round(2), bob))

        val onY = reader.findAppUser(AllTime, appY, alice)!!
        assertAmount("1", onY.totalRewardAmount)
        assertEquals(alice, onY.user)
        assertNull(reader.findAppUser(Day("2026-09-01"), appY, bob))
    }

    @Test
    fun `leaderboards page past a cursor with the entity breaking ties`() {
        val byActions =
            reader.leaderboard(
                AllTime,
                EntityType.USER,
                ActionSortField.ACTIONS_REWARDED,
                Direction.DESC,
                10,
                null,
                null,
            )
        assertEquals(listOf(alice, bob, carol), byActions.map { it.entity })

        val afterBob =
            reader.leaderboard(
                AllTime,
                EntityType.USER,
                ActionSortField.ACTIONS_REWARDED,
                Direction.DESC,
                10,
                "1",
                bob,
            )
        assertEquals(listOf(carol), afterBob.map { it.entity })

        val byRewardAsc =
            reader.leaderboard(
                Round(3),
                EntityType.USER,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                Direction.ASC,
                2,
                null,
                null,
            )
        assertEquals(listOf(carol, bob), byRewardAsc.map { it.entity })
        val afterBobAsc =
            reader.leaderboard(
                Round(3),
                EntityType.USER,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                Direction.ASC,
                2,
                "5",
                bob,
            )
        assertEquals(listOf(alice), afterBobAsc.map { it.entity })

        val apps =
            reader.leaderboard(
                AllTime,
                EntityType.APP,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                Direction.DESC,
                10,
                null,
                null,
            )
        assertEquals(listOf(appX, appY), apps.map { it.entity })

        val onX =
            reader.appLeaderboard(
                AllTime,
                appX,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                Direction.DESC,
                10,
                null,
                null,
            )
        assertEquals(listOf(alice, bob, carol), onX.map { it.user })
        val onXAfterBob =
            reader.appLeaderboard(
                AllTime,
                appX,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                Direction.DESC,
                10,
                "5",
                bob,
            )
        assertEquals(listOf(carol), onXAfterBob.map { it.user })
    }

    @Test
    fun `ranks count the entities ahead`() {
        assertEquals(
            1L,
            reader.countEntitiesAbove(
                AllTime,
                EntityType.USER,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                BigDecimal("5"),
            ),
        )
        assertEquals(
            0L,
            reader.countEntitiesAbove(
                AllTime,
                EntityType.USER,
                ActionSortField.ACTIONS_REWARDED,
                BigDecimal("2"),
            ),
        )
        assertEquals(
            1L,
            reader.countEntitiesAbove(
                Day("2026-09-01"),
                EntityType.USER,
                ActionSortField.ACTIONS_REWARDED,
                BigDecimal("1"),
            ),
        )
        assertEquals(
            2L,
            reader.countAppUsersAbove(
                AllTime,
                appX,
                ActionSortField.TOTAL_REWARD_AMOUNT,
                BigDecimal("3"),
            ),
        )
        assertEquals(
            0L,
            reader.countAppUsersAbove(
                Round(3),
                appY,
                ActionSortField.ACTIONS_REWARDED,
                BigDecimal("1"),
            ),
        )
    }

    @Test
    fun `a wallet's days come back in the range asked for`() {
        val days = reader.findDailyRange(alice, "2026-08-01", "2026-09-30", 0, 10, Direction.ASC)
        assertEquals(listOf("2026-08-31", "2026-09-01"), days.map { it.period.date })
        assertEquals(listOf(1L, 2L), days.map { it.actionsRewarded })
        assertEquals(
            listOf("2026-09-01"),
            reader.findDailyRange(alice, "2026-09-01", "2026-09-30", 0, 10, Direction.DESC).map {
                it.period.date
            },
        )
        assertEquals(
            1,
            reader.findDailyRange(alice, "2026-08-01", "2026-09-30", 1, 10, Direction.DESC).size,
        )
    }

    @Test
    fun `the newest round on record is the GLOBAL row's`() {
        assertEquals(3, reader.latestRound())
    }

    @Test
    fun `the indexer reads the current rows of the keys it touches`() {
        val stranger = "0x" + "dd".repeat(20)
        val entities =
            writer.findCurrentEntities(
                AllTime,
                setOf(
                    EntityType.USER to alice,
                    EntityType.APP to appY,
                    EntityType.GLOBAL to global,
                    EntityType.USER to stranger,
                ),
            )
        assertEquals(
            setOf(EntityType.USER to alice, EntityType.APP to appY, EntityType.GLOBAL to global),
            entities.map { it.entityType to it.entity }.toSet(),
        )
        assertEquals(setOf(20L), entities.map { it.blockNumber }.toSet())
        assertEquals(
            listOf(alice),
            writer.findCurrentAppUsers(Round(3), setOf(appX to alice, appY to bob)).map { it.user },
        )
        assertEquals(
            emptyList<EntityActionSummary>(),
            writer.findCurrentEntities(Round(9), setOf(EntityType.USER to alice)),
        )
    }

    @Test
    fun `rollback reopens the rows the block superseded`() {
        writer.rollbackFrom(20)

        val aliceAllTime = reader.findEntity(AllTime, EntityType.USER, alice)!!
        assertEquals(10L, aliceAllTime.blockNumber)
        assertEquals(1L, aliceAllTime.actionsRewarded)
        assertEquals(3L, reader.findEntity(Round(3), EntityType.GLOBAL, global)!!.actionsRewarded)
        assertNull(reader.findEntity(AllTime, EntityType.APP, appY))
        assertEquals(listOf(appX), reader.findAppIds(AllTime, alice))

        writer.truncate()
        assertNull(reader.latestRound())
        seed()
    }

    @Test
    fun `a key the entry touches on several blocks leaves one open row per rollback point`() {
        writer.truncate()
        val dave = "0x" + "ee".repeat(20)
        val blocks = listOf(30L, 40L, 50L)
        writer.save(
            ActionSummaryUpdate(
                entities =
                    blocks.mapIndexed { i, b ->
                        entity(EntityType.USER, dave, AllTime, b, i + 1L, "${i + 1}")
                    },
                appUsers =
                    blocks.mapIndexed { i, b ->
                        appUser(appX, dave, AllTime, b, i + 1L, "${i + 1}")
                    },
            )
        )
        assertEquals(1, open("entity_all_time"))
        assertEquals(1, open("app_user_all_time"))
        assertEquals(50L, reader.findEntity(AllTime, EntityType.USER, dave)!!.blockNumber)
        assertEquals(50L, reader.findAppUser(AllTime, appX, dave)!!.blockNumber)

        writer.rollbackFrom(50)
        assertEquals(1, open("entity_all_time"))
        assertEquals(40L, reader.findEntity(AllTime, EntityType.USER, dave)!!.blockNumber)
        assertEquals(40L, reader.findAppUser(AllTime, appX, dave)!!.blockNumber)

        writer.rollbackFrom(40)
        assertEquals(30L, reader.findEntity(AllTime, EntityType.USER, dave)!!.blockNumber)

        writer.truncate()
        seed()
    }

    @Test
    fun `replaying an entry over the same or a wider range leaves one open row`() {
        val frank = "0x" + "ab".repeat(20)
        fun chain(vararg blocks: Long) =
            ActionSummaryUpdate(
                entities = blocks.map { entity(EntityType.USER, frank, AllTime, it, it, "$it") },
                appUsers = blocks.map { appUser(appX, frank, AllTime, it, it, "$it") },
            )
        fun openRowsOf(user: String) =
            database.jdbc.queryForObject(
                "SELECT count(*) FROM b3tr_action.entity_all_time " +
                    "WHERE superseded_at IS NULL AND entity = decode(?, 'hex')",
                Int::class.java,
                user.removePrefix("0x"),
            )!!

        writer.truncate()
        writer.save(chain(100, 130))
        writer.save(chain(100, 130))
        assertEquals(1, openRowsOf(frank), "same range")
        assertEquals(130L, reader.findEntity(AllTime, EntityType.USER, frank)!!.blockNumber)

        writer.save(chain(100, 130, 150))
        assertEquals(1, openRowsOf(frank), "longer range")
        assertEquals(150L, reader.findEntity(AllTime, EntityType.USER, frank)!!.blockNumber)

        writer.truncate()
        seed()
    }

    private fun open(table: String) =
        database.jdbc.queryForObject(
            "SELECT count(*) FROM b3tr_action.$table WHERE superseded_at IS NULL",
            Int::class.java,
        )!!

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(20))
        // Alice's row and the GLOBAL row that block 20 replaced, in each of the three periods.
        assertEquals(6, writer.prune(21))
        assertEquals(20L, reader.findEntity(AllTime, EntityType.USER, alice)?.blockNumber)
        writer.truncate()
        seed()
    }
}
