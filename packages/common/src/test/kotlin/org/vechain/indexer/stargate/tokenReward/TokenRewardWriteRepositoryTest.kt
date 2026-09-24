package org.vechain.indexer.stargate.tokenReward

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenRewardWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: TokenRewardWriteRepository

    private val validator = "0x" + "1".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = TokenRewardWriteRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun tracker(tokenId: String, block: Long, rewards: Long, cycle: Long = 3) =
        TokenReward(
            id = "$validator-$tokenId",
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            tokenId = tokenId,
            cycle = cycle,
            validator = validator,
            rewards = BigInteger.valueOf(rewards),
            effectiveStake =
                BigInteger(
                    "1000000000000000000000000000000000000000000000000000000000000000000000000000000"
                ) - BigInteger.ONE,
            rewardPeriod = RewardPeriod.ALL,
            dayOfMonth = 25,
            weekOfYear = 43,
            month = 10,
            year = 2025,
            dayReward = BigInteger.valueOf(rewards),
            cycleReward = BigInteger.ZERO,
        )

    private fun period(tracker: TokenReward, period: RewardPeriod, block: Long) =
        tracker.copy(
            id = "${tracker.id}-${period.name.lowercase()}-2025-10-24",
            blockNumber = block,
            blockTimestamp = block * 10,
            rewardPeriod = period,
            effectiveStake = null,
            dayReward = null,
            cycleReward = null,
        )

    /** Every row as "id:block:supersededAt", ordered. */
    private fun rows(): List<String> =
        database.jdbc.queryForList(
            "SELECT id || ':' || block_number || ':' || coalesce(superseded_at::text, '-') " +
                "FROM token_reward.state ORDER BY id, block_number",
            String::class.java,
        )

    @Test
    fun `a supersede is planned on the current-row index, not the key`() {
        // Versions per tracker, and closed periods whose unique ids make each id look rare.
        (10L..14L).forEach { block ->
            writer.save((1L..300L).map { tracker(it.toString(), block, it) })
        }
        (1L..30L).forEach { day ->
            writer.save(
                (1L..300L).map {
                    period(tracker(it.toString(), day, it), RewardPeriod.DAY, day)
                        .copy(id = "$validator-$it-day-$day")
                }
            )
        }
        database.jdbc.execute("ANALYZE token_reward.state")

        // pgjdbc goes generic after prepareThreshold; a literal EXPLAIN never sees that plan.
        val prepared = TokenRewardWriteRepository.SUPERSEDE.split("?")
        val sql = prepared.dropLast(1).mapIndexed { i, part -> "$part\$${i + 1}" }.joinToString("")
        val ids = (1..300).joinToString(",", "ARRAY[", "]::varchar[]") { "'$validator-$it'" }
        val plan =
            database.dataSource.connection.use { c ->
                c.createStatement().execute("SET plan_cache_mode = force_generic_plan")
                c.createStatement()
                    .execute("PREPARE s(bigint, varchar[], bigint) AS $sql${prepared.last()}")
                c.createStatement().executeQuery("EXPLAIN EXECUTE s(15, $ids, 15)").use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.joinToString("\n")
                }
            }

        assertTrue(plan.contains("state_current_id_idx"), plan)
        assertTrue(!plan.contains("state_pkey"), plan)
    }

    @Test
    fun `every field survives the round trip`() {
        val all = tracker("7", 10, 100)
        val day = period(all, RewardPeriod.DAY, 10)

        writer.save(listOf(all, day))

        assertEquals(listOf(all, day), writer.findAllById(listOf(all.id, day.id)))
    }

    @Test
    fun `a tracker accrues as a new row per block, rollback reopens the old one`() {
        writer.save(listOf(tracker("7", 10, 100)))
        writer.save(listOf(tracker("7", 11, 110), tracker("8", 11, 5)))
        writer.save(listOf(tracker("7", 12, 120)))

        assertEquals(
            listOf(
                "$validator-7:10:11",
                "$validator-7:11:12",
                "$validator-7:12:-",
                "$validator-8:11:-",
            ),
            rows(),
        )
        assertEquals(
            listOf(tracker("7", 12, 120), tracker("8", 11, 5)),
            writer.findAllById(listOf("$validator-7", "$validator-8")),
        )

        writer.rollbackFrom(12)

        assertEquals(
            listOf(tracker("7", 11, 110), tracker("8", 11, 5)),
            writer.findAllById(listOf("$validator-7", "$validator-8")),
        )
    }

    @Test
    fun `the cycle trackers of a validator are its current ALL rows for that cycle`() {
        writer.save(listOf(tracker("7", 10, 100, cycle = 3), tracker("8", 10, 5, cycle = 2)))
        writer.save(
            listOf(
                tracker("7", 11, 110, cycle = 3),
                period(tracker("7", 11, 110), RewardPeriod.CYCLE, 11),
            )
        )

        assertEquals(
            listOf(tracker("7", 11, 110, cycle = 3)),
            writer.findAllByValidatorAndRewardPeriodAndCycle(validator, RewardPeriod.ALL, 3),
        )
        assertEquals(emptyList<TokenReward>(), writer.findAllById(emptyList()))
    }

    @Test
    fun `the indexer's own reads still work with the deferrable indexes dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(TokenRewardIndexes.SET)
        try {
            writer.save(listOf(tracker("7", 10, 100)))
            writer.save(listOf(tracker("7", 11, 110)))

            assertEquals(
                listOf(tracker("7", 11, 110)),
                writer.findAllByValidatorAndRewardPeriodAndCycle(validator, RewardPeriod.ALL, 3),
            )
            assertEquals(listOf(tracker("7", 11, 110)), writer.findAllById(listOf("$validator-7")))

            writer.rollbackFrom(11)
            assertEquals(listOf("$validator-7:10:-"), rows())
        } finally {
            builder.build(TokenRewardIndexes.SET)
        }
    }

    @Test
    fun `replay is a no-op and prune drops old superseded rows`() {
        writer.save(listOf(tracker("7", 10, 100)))
        writer.save(listOf(tracker("7", 20, 200)))
        writer.save(listOf(tracker("7", 30, 300)))
        val before = rows()

        writer.save(listOf(tracker("7", 20, 200)))
        assertEquals(before, rows())

        assertEquals(1, writer.prune(before = 25))
        assertEquals(listOf("$validator-7:20:30", "$validator-7:30:-"), rows())
    }

    @Test
    fun `truncate empties the table`() {
        writer.save(listOf(tracker("7", 10, 100)))
        writer.truncate()
        assertTrue(rows().isEmpty())
    }
}
