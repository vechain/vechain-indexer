package org.vechain.indexer.validator

import java.math.BigDecimal
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.PostgresTestDatabase

/** One test per read on a seeded set; see [seed] for who is what. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidatorReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: ValidatorReadRepository

    private val active = "0x" + "1".repeat(40)
    private val queued = "0x" + "2".repeat(40)
    private val exiting = "0x" + "3".repeat(40)
    private val exited = "0x" + "4".repeat(40)
    private val unknown = "0x" + "5".repeat(40)
    private val endorser = "0x" + "e".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        repository = ValidatorReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun validator(
        id: String,
        status: Status?,
        stake: BigDecimal?,
        block: Long = 10,
        endorser: String? = null,
        lastMissed: Long? = null,
    ) =
        Validator(
            id = id,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            endorser = endorser,
            status = status,
            validatorVetStaked = stake,
            lastMissedBlockNumber = lastMissed,
        )

    /**
     * active (stake 300, endorsed), queued (200, endorsed), exiting (100), exited (400, missed at
     * 50), and an unknown row with no status or stake; the active row was re-stated at block 20.
     */
    private fun seed() {
        val writer = ValidatorWriteRepository(database.jdbc)
        writer.save(
            listOf(
                validator(active, Status.ACTIVE, BigDecimal("250"), endorser = endorser),
                validator(queued, Status.QUEUED, BigDecimal("200"), endorser = endorser)
                    .copy(cyclePeriodLength = 7),
                validator(exiting, Status.EXITING, BigDecimal("100")),
                validator(exited, Status.EXITED, BigDecimal("400"), lastMissed = 50),
                validator(unknown, null, null),
            )
        )
        writer.save(
            listOf(
                validator(active, Status.ACTIVE, BigDecimal("300"), block = 20, endorser = endorser)
            )
        )
    }

    private fun ids(validators: List<Validator>) = validators.map { it.id }

    @Test
    fun `findAll returns the current row of every validator`() {
        val all = repository.findAll()
        assertEquals(listOf(active, queued, exiting, exited, unknown), ids(all))
        assertEquals(BigDecimal("300"), all.first().validatorVetStaked)
    }

    @Test
    fun `snapshotsSince with no watermark is the current set`() {
        val rows = repository.snapshotsSince(null)
        assertEquals(
            setOf(active, queued, exiting, exited, unknown),
            rows.map { it.snapshot.validatorId }.toSet(),
        )
        assertTrue(rows.all { it.current })
        assertEquals(20L, rows.single { it.snapshot.validatorId == active }.block.number)
        assertEquals(
            7L,
            rows.single { it.snapshot.validatorId == queued }.snapshot.stakingPeriodLength,
        )
    }

    @Test
    fun `snapshotsSince returns the rows since the watermark and the watermark rows themselves`() {
        val rows = repository.snapshotsSince(10)
        val atWatermark = rows.filter { it.block.number == 10L }
        assertEquals(5, atWatermark.size)
        assertEquals("0x" + "0".repeat(62) + "0a", atWatermark.first().block.id)
        assertFalse(atWatermark.single { it.snapshot.validatorId == active }.current)
        assertEquals(
            listOf(active),
            rows.filter { it.block.number > 10L }.map { it.snapshot.validatorId },
        )
        assertEquals(listOf(active), repository.snapshotsSince(20).map { it.snapshot.validatorId })
    }

    @Test
    fun `findById is case-insensitive and null for an unknown address`() {
        assertEquals(20L, repository.findById(active.uppercase().replace("0X", "0x"))?.blockNumber)
        assertNull(repository.findById("0x" + "9".repeat(40)))
    }

    @Test
    fun `findAllById skips the ids it does not hold`() {
        assertEquals(
            listOf(active, exited),
            ids(repository.findAllById(listOf(exited, active, "0x" + "9".repeat(40)))),
        )
        assertEquals(emptyList<Validator>(), repository.findAllById(emptyList()))
    }

    @Test
    fun `findByStatusIn matches the enum`() {
        assertEquals(
            listOf(active, queued, exiting),
            ids(repository.findByStatusIn(listOf(Status.ACTIVE, Status.QUEUED, Status.EXITING))),
        )
    }

    @Test
    fun `findByLastMissedBlockNumber is a point lookup`() {
        assertEquals(listOf(exited), ids(repository.findByLastMissedBlockNumber(50)))
        assertEquals(emptyList<Validator>(), repository.findByLastMissedBlockNumber(51))
    }

    private fun page(
        id: String? = null,
        endorser: String? = null,
        statuses: List<Status>? = null,
        sort: String = Validator::validatorVetStaked.name,
        direction: Direction = Direction.DESC,
        offset: Long = 0,
        limit: Int = 10,
    ) = ids(repository.find(id, endorser, statuses, sort, direction, offset, limit))

    @Test
    fun `the default page is stake descending with unknown stakes last`() {
        assertEquals(listOf(exited, active, queued, exiting, unknown), page())
    }

    @Test
    fun `ascending puts the unknown stake first as Mongo did`() {
        assertEquals(
            listOf(unknown, exiting, queued, active, exited),
            page(direction = Direction.ASC),
        )
    }

    @Test
    fun `offset and limit page through, one row past the page included`() {
        assertEquals(listOf(active, queued), page(offset = 1, limit = 2))
    }

    @Test
    fun `filters narrow by id, endorser and status`() {
        assertEquals(listOf(queued), page(id = queued))
        assertEquals(listOf(active, queued), page(endorser = endorser))
        assertEquals(listOf(active), page(endorser = endorser, statuses = listOf(Status.ACTIVE)))
        assertEquals(emptyList<String>(), page(statuses = listOf(Status.NONE)))
    }

    @Test
    fun `every V1 sort field has a column and nothing else does`() {
        for (field in ValidatorReadRepository.SORT_COLUMNS.keys) page(sort = field)
        assertThrows<IllegalArgumentException> { page(sort = "validatorTvl") }
    }
}
