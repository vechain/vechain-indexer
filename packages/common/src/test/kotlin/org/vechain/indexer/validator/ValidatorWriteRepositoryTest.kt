package org.vechain.indexer.validator

import java.math.BigDecimal
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
class ValidatorWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ValidatorWriteRepository
    private lateinit var reader: ValidatorReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = ValidatorWriteRepository(database.jdbc)
        reader = ValidatorReadRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun validator(id: String, block: Long, status: Status = Status.ACTIVE) =
        Validator(
            id = id,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            status = status,
            validatorVetStaked = BigDecimal("25000000.000000000000"),
            scheduledSlots = block,
            proposedBlocks = block,
        )

    /** Every row as "id:block:status:supersededAt", ordered. */
    private fun rows(): List<String> =
        database.jdbc.queryForList(
            "SELECT encode(id, 'hex') || ':' || block_number || ':' || status || ':' || " +
                "coalesce(superseded_at::text, '-') FROM validator.state ORDER BY id, block_number",
            String::class.java,
        )

    /** Every cycle row as "id:block:status:supersededAt", ordered. */
    private fun cycles(): List<String> =
        database.jdbc.queryForList(
            "SELECT encode(id, 'hex') || ':' || block_number || ':' || status || ':' || " +
                "coalesce(superseded_at::text, '-') FROM validator.cycle ORDER BY id, block_number",
            String::class.java,
        )

    @Test
    fun `a cycle row lands only when a field other indexers read changes, and prune keeps it`() {
        writer.save(listOf(validator(alice, 10)))
        writer.save(listOf(validator(alice, 20)))
        writer.save(listOf(validator(alice, 30, Status.EXITING)))
        writer.save(listOf(validator(alice, 40, Status.EXITING)))

        writer.prune(before = 100)

        assertEquals(
            listOf("${"a".repeat(40)}:10:ACTIVE:30", "${"a".repeat(40)}:30:EXITING:-"),
            cycles(),
        )
    }

    @Test
    fun `rollback reopens the cycle row it closed and a replay rewrites its own`() {
        writer.save(listOf(validator(alice, 10)))
        writer.save(listOf(validator(alice, 20, Status.EXITING)))

        writer.rollbackFrom(20)
        assertEquals(listOf("${"a".repeat(40)}:10:ACTIVE:-"), cycles())

        writer.save(listOf(validator(alice, 20, Status.QUEUED)))
        writer.save(listOf(validator(alice, 20, Status.EXITING)))
        assertEquals(
            listOf("${"a".repeat(40)}:10:ACTIVE:20", "${"a".repeat(40)}:20:EXITING:-"),
            cycles(),
        )
    }

    @Test
    fun `every field survives the round trip, scale included`() {
        val full =
            Validator(
                id = alice,
                blockId = "0x" + "1".repeat(64),
                blockNumber = 10,
                blockTimestamp = 100,
                endorser = bob,
                beneficiary = "0x" + "c".repeat(40),
                status = Status.EXITING,
                cyclePeriodLength = 25_920,
                startBlock = 23_414_400,
                exitBlock = 23_440_320,
                completedPeriods = 3,
                validatorVetStaked = BigDecimal("25000000.000000000000"),
                validatorLockedWeight = BigDecimal("97620000.000000000000"),
                delegatorVetStaked = BigDecimal("50000000.500000000000"),
                vetStaked = BigDecimal("75000000.500000000000"),
                validatorQueuedVetStaked = BigDecimal.ZERO,
                queuedVetStaked = BigDecimal("1.000000000001"),
                exitingVetStaked = BigDecimal("35000000"),
                validatorExitingVetStaked = BigDecimal("25000000"),
                totalNextPeriodWeight = BigDecimal("100000000"),
                queuePosition = 2,
                availableStartBlock = 23_500_000,
                scheduledSlots = 1_000,
                proposedBlocks = 990,
                missedSlots = 10,
                lastProposedBlockNumber = 9,
                lastMissedBlockNumber = 8,
                offlineBlock = 7,
            )
        val sparse = validator(bob, 10).copy(status = null, validatorVetStaked = null)

        writer.save(listOf(full, sparse))

        assertEquals(listOf(full, sparse), reader.findAll())
    }

    @Test
    fun `a later block closes the open row and leaves the newest current`() {
        writer.save(listOf(validator(alice, 10), validator(bob, 10)))
        writer.save(listOf(validator(alice, 20, Status.EXITING)))

        assertEquals(
            listOf(validator(alice, 20, Status.EXITING), validator(bob, 10)),
            reader.findAll(),
        )
        assertEquals(
            listOf(
                "${"a".repeat(40)}:10:ACTIVE:20",
                "${"a".repeat(40)}:20:EXITING:-",
                "${"b".repeat(40)}:10:ACTIVE:-",
            ),
            rows(),
        )
    }

    @Test
    fun `rollback at depth reopens the rows the rolled-back blocks had closed`() {
        writer.save(listOf(validator(alice, 10)))
        writer.save(listOf(validator(alice, 20)))
        writer.save(listOf(validator(alice, 30), validator(bob, 30)))

        writer.rollbackFrom(20)

        assertEquals(listOf(validator(alice, 10)), reader.findAll())
        assertEquals(listOf("${"a".repeat(40)}:10:ACTIVE:-"), rows())
    }

    @Test
    fun `replaying a block changes nothing`() {
        writer.save(listOf(validator(alice, 10)))
        writer.save(listOf(validator(alice, 20)))
        val before = rows()

        writer.save(listOf(validator(alice, 20)))
        writer.save(listOf(validator(alice, 10)))

        assertEquals(before, rows())
    }

    @Test
    fun `prune keeps the current rows and those superseded inside the window`() {
        writer.save(listOf(validator(alice, 10)))
        writer.save(listOf(validator(alice, 20)))
        writer.save(listOf(validator(alice, 30)))

        assertEquals(1, writer.prune(before = 25))

        assertEquals(
            listOf("${"a".repeat(40)}:20:ACTIVE:30", "${"a".repeat(40)}:30:ACTIVE:-"),
            rows(),
        )
    }

    @Test
    fun `the write path holds up with the deferrable index dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(ValidatorIndexes.SET)
        try {
            writer.save(listOf(validator(alice, 10)))
            writer.save(listOf(validator(alice, 20, Status.EXITING)))

            assertEquals(2, rows().size)
            writer.rollbackFrom(20)
            assertEquals(listOf("${alice.removePrefix("0x")}:10:ACTIVE:-"), rows())
            writer.prune(30)
        } finally {
            builder.build(ValidatorIndexes.SET)
        }
    }

    @Test
    fun `truncate empties the table`() {
        writer.save(listOf(validator(alice, 10)))
        writer.truncate()
        assertTrue(rows().isEmpty())
    }
}
