package org.vechain.indexer.stargate.token

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
import org.vechain.indexer.thor.Address

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StargateTokenWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: StargateTokenWriteRepository

    private val owner = "0x" + "a".repeat(40)
    private val manager = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = StargateTokenWriteRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun token(tokenId: String, block: Long, owner: String = this.owner) =
        StargateToken(
            tokenId = tokenId,
            level = TokenLevel.ThunderX,
            owner = owner,
            manager = manager,
            totalRewardsClaimed = BigInteger("123456789012345678901234567890"),
            totalBootstrapRewardsClaimed = BigInteger.ONE,
            vetStaked = BigInteger("5600000000000000000000000"),
            migrated = true,
            boosted = false,
            blockNumber = block,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockTimestamp = block * 10,
        )

    /** Every row as "token:block:supersededAt", ordered. */
    private fun rows(): List<String> =
        database.jdbc.queryForList(
            "SELECT token_id::text || ':' || block_number || ':' || " +
                "coalesce(superseded_at::text, '-') FROM stargate_token.state ORDER BY token_id, block_number",
            String::class.java,
        )

    @Test
    fun `every field survives the round trip`() {
        val full = token("1", 10).copy(boosted = true)
        val sparse = token("2", 10).copy(manager = null)

        writer.save(listOf(full, sparse))

        assertEquals(listOf(full, sparse), writer.findAllById(setOf("1", "2")))
    }

    @Test
    fun `a later block closes the open row, rollback reopens it and replay is a no-op`() {
        writer.save(listOf(token("1", 10), token("2", 10)))
        writer.save(listOf(token("1", 20)))
        val before = rows()

        writer.save(listOf(token("1", 20)))
        assertEquals(before, rows())
        assertEquals(listOf("1:10:20", "1:20:-", "2:10:-"), rows())

        writer.rollbackFrom(20)
        assertEquals(listOf("1:10:-", "2:10:-"), rows())
    }

    @Test
    fun `the indexer's read sees only current rows, with the deferrable indexes dropped too`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(StargateTokenIndexes.SET)
        try {
            writer.save(listOf(token("1", 10), token("2", 10)))
            writer.save(listOf(token("1", 20)))

            assertEquals(
                listOf(20L, 10L),
                writer.findAllById(setOf("1", "2", "3")).map { it.blockNumber },
            )

            writer.rollbackFrom(20)
            assertEquals(listOf("1:10:-", "2:10:-"), rows())
        } finally {
            builder.build(StargateTokenIndexes.SET)
        }
    }

    @Test
    fun `prune keeps the current rows and those superseded inside the window`() {
        writer.save(listOf(token("1", 10)))
        writer.save(listOf(token("1", 20)))
        writer.save(listOf(token("1", 30)))

        assertEquals(1, writer.prune(before = 25))

        assertEquals(listOf("1:20:30", "1:30:-"), rows())
    }

    @Test
    fun `truncate empties the table`() {
        writer.save(listOf(token("1", 10, owner = Address.ZERO_ADDRESS)))
        writer.truncate()
        assertTrue(rows().isEmpty())
    }
}
