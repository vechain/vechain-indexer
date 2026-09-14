package org.vechain.indexer.stargate.tokenReward

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.PostgresTestDatabase

/** One test per API read on a seeded set; see [seed] for the records of token 7. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenRewardReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: TokenRewardReadRepository

    private val alice = "0x" + "1".repeat(40)
    private val bob = "0x" + "2".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        repository = TokenRewardReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun record(
        id: String,
        block: Long,
        validator: String,
        period: RewardPeriod,
        tokenId: String = "7",
    ) =
        TokenReward(
            id = id,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            tokenId = tokenId,
            cycle = 1,
            validator = validator,
            rewards = BigInteger.valueOf(block),
            rewardPeriod = period,
            dayOfMonth = 25,
            weekOfYear = 43,
            month = 10,
            year = 2025,
        )

    /**
     * Token 7: alice's ALL tracker (blocks 10 then 30), alice's DAY at 20, bob's ALL at 40 and WEEK
     * at 50; token 8: alice's ALL at 60.
     */
    private fun seed() {
        val writer = TokenRewardWriteRepository(database.jdbc)
        writer.save(
            listOf(
                record("a-7", 10, alice, RewardPeriod.ALL),
                record("a-7-day", 20, alice, RewardPeriod.DAY),
                record("a-7", 30, alice, RewardPeriod.ALL),
                record("b-7", 40, bob, RewardPeriod.ALL),
                record("b-7-week", 50, bob, RewardPeriod.WEEK),
                record("a-8", 60, alice, RewardPeriod.ALL, tokenId = "8"),
            )
        )
    }

    private fun ids(rows: List<TokenReward>) = rows.map { it.id }

    @Test
    fun `the token's records in the requested periods, newest first, one row per current record`() {
        assertEquals(
            listOf("b-7", "a-7"),
            ids(
                repository.findByTokenIdAndRewardPeriodIn(
                    "7",
                    listOf(RewardPeriod.ALL),
                    null,
                    Direction.DESC,
                    0,
                    10,
                )
            ),
        )
        assertEquals(
            listOf("a-7-day", "a-7", "b-7"),
            ids(
                repository.findByTokenIdAndRewardPeriodIn(
                    "7",
                    listOf(RewardPeriod.DAY, RewardPeriod.ALL),
                    null,
                    Direction.ASC,
                    0,
                    10,
                )
            ),
        )
    }

    @Test
    fun `a validator narrows the records and offset pages them`() {
        assertEquals(
            listOf("b-7-week", "b-7"),
            ids(
                repository.findByTokenIdAndRewardPeriodIn(
                    "7",
                    listOf(RewardPeriod.WEEK, RewardPeriod.ALL),
                    bob,
                    Direction.DESC,
                    0,
                    10,
                )
            ),
        )
        assertEquals(
            listOf("a-7"),
            ids(
                repository.findByTokenIdAndRewardPeriodIn(
                    "7",
                    listOf(RewardPeriod.ALL),
                    null,
                    Direction.DESC,
                    1,
                    10,
                )
            ),
        )
    }
}
