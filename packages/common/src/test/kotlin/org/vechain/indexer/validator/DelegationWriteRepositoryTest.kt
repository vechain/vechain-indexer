package org.vechain.indexer.validator

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
import org.vechain.indexer.stargate.token.TokenLevel

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelegationWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: DelegationWriteRepository

    private val validator = "0x" + "1".repeat(40)
    private val other = "0x" + "2".repeat(40)
    private val owner = "0x" + "a".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = DelegationWriteRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun delegation(
        id: String,
        block: Long,
        status: DelegationStatus = DelegationStatus.QUEUED,
        transitionAt: Long? = null,
        validator: String = this.validator,
        tokenId: String = id,
    ) =
        Delegation(
            id = id,
            validator = validator,
            tokenId = tokenId,
            owner = owner,
            status = status,
            tokenLevel = TokenLevel.Mjolnir,
            stakedAmount = "15000000000000000000000000",
            totalRewardsClaimed = BigInteger("123456789012345678901234567890"),
            txId = "0x" + block.toString(16).padStart(64, '0'),
            transitionAtBlock = transitionAt,
            initiatedAtBlock = 5,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )

    /** Every row as "id:block:status:supersededAt", ordered. */
    private fun rows(): List<String> =
        database.jdbc.queryForList(
            "SELECT id::text || ':' || block_number || ':' || status || ':' || " +
                "coalesce(superseded_at::text, '-') FROM delegation.state ORDER BY id, block_number",
            String::class.java,
        )

    private fun current(): List<Delegation> =
        database.jdbc.query(
            "SELECT * FROM delegation.state WHERE superseded_at IS NULL ORDER BY id"
        ) { rs, _ ->
            DelegationRowMapping.read(rs)
        }

    @Test
    fun `the active set is the current ACTIVE and EXITING rows`() {
        writer.save(
            listOf(
                delegation("1", 10, DelegationStatus.ACTIVE),
                delegation("2", 10),
                delegation("3", 10, DelegationStatus.EXITING),
                delegation("4", 10, DelegationStatus.ACTIVE),
            )
        )
        writer.save(listOf(delegation("4", 20, DelegationStatus.EXITED)))

        assertEquals(listOf("1", "3"), writer.findActive().map { it.id })
    }

    @Test
    fun `every field survives the round trip, uint256 ids and wei included`() {
        val big = "115792089237316195423570985008687907853269984665640564039457584007913129639935"
        val rows =
            listOf(
                delegation(big, 10, DelegationStatus.ACTIVE, transitionAt = 900, tokenId = big),
                delegation("7", 10),
            )

        writer.save(rows)

        assertEquals(rows.reversed(), current())
    }

    @Test
    fun `a later block closes the open row and leaves the newest current`() {
        writer.save(listOf(delegation("1", 10), delegation("2", 10)))
        writer.save(listOf(delegation("1", 20, DelegationStatus.ACTIVE)))

        assertEquals(
            listOf(delegation("1", 20, DelegationStatus.ACTIVE), delegation("2", 10)),
            current(),
        )
        assertEquals(listOf("1:10:QUEUED:20", "1:20:ACTIVE:-", "2:10:QUEUED:-"), rows())
    }

    @Test
    fun `rollback at depth reopens the rows the rolled-back blocks had closed`() {
        writer.save(listOf(delegation("1", 10)))
        writer.save(listOf(delegation("1", 20, DelegationStatus.ACTIVE)))
        writer.save(listOf(delegation("1", 30, DelegationStatus.EXITING), delegation("2", 30)))

        writer.rollbackFrom(20)

        assertEquals(listOf(delegation("1", 10)), current())
        assertEquals(listOf("1:10:QUEUED:-"), rows())
    }

    @Test
    fun `replaying a block changes nothing and prune drops old superseded rows`() {
        writer.save(listOf(delegation("1", 10)))
        writer.save(listOf(delegation("1", 20, DelegationStatus.ACTIVE)))
        writer.save(listOf(delegation("1", 30, DelegationStatus.EXITING)))
        val before = rows()

        writer.save(listOf(delegation("1", 20, DelegationStatus.ACTIVE)))
        assertEquals(before, rows())

        assertEquals(1, writer.prune(before = 25))
        assertEquals(listOf("1:20:ACTIVE:30", "1:30:EXITING:-"), rows())
    }

    @Test
    fun `the indexer's own reads see only current rows`() {
        writer.save(
            listOf(
                delegation("1", 10, transitionAt = 100),
                delegation("2", 10),
                delegation(
                    "3",
                    10,
                    DelegationStatus.EXITING,
                    transitionAt = 100,
                    validator = other,
                ),
                delegation("4", 10, DelegationStatus.EXITED, tokenId = "40"),
            )
        )
        writer.save(listOf(delegation("2", 20, DelegationStatus.ACTIVE)))

        val queuedOrExiting = listOf(DelegationStatus.QUEUED, DelegationStatus.EXITING)
        assertEquals(
            listOf("1", "3"),
            writer.findByTransitionAtBlockAndStatusIn(100, queuedOrExiting).map { it.id },
        )
        assertEquals(
            emptyList<String>(),
            writer.findByTransitionAtBlockIsNullAndStatusIn(listOf(DelegationStatus.QUEUED)).map {
                it.id
            },
        )
        assertEquals(listOf("2", "4"), writer.findByTokenIdIn(listOf("2", "40")).map { it.id })
        assertEquals(listOf("3"), writer.findByValidatorIn(listOf(other)).map { it.id })
        assertEquals(20L, writer.findByTokenIdIn(listOf("2")).single().blockNumber)
    }

    @Test
    fun `the indexer's own reads still work with the deferrable index dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(DelegationIndexes.SET)
        try {
            writer.save(listOf(delegation("1", 10, transitionAt = 100), delegation("2", 10)))

            assertEquals(
                listOf("1"),
                writer
                    .findByTransitionAtBlockAndStatusIn(100, listOf(DelegationStatus.QUEUED))
                    .map { it.id },
            )
            assertEquals(
                listOf("2"),
                writer
                    .findByTransitionAtBlockIsNullAndStatusIn(listOf(DelegationStatus.QUEUED))
                    .map {
                        it.id
                    },
            )
            assertEquals(listOf("1"), writer.findByTokenIdIn(listOf("1")).map { it.id })
            assertEquals(
                listOf("1", "2"),
                writer.findByValidatorIn(listOf(validator)).map { it.id },
            )

            writer.rollbackFrom(10)
            assertEquals(emptyList<String>(), rows())
        } finally {
            builder.build(DelegationIndexes.SET)
        }
    }

    @Test
    fun `truncate empties the table`() {
        writer.save(listOf(delegation("1", 10)))
        writer.truncate()
        assertTrue(rows().isEmpty())
    }
}
