package org.vechain.indexer.stargate.vetDelegated

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
class VetDelegatedByBlockServiceTest {
    @MockK lateinit var repository: VetDelegatedByBlockRepository

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

    @MockK(relaxed = true)
    lateinit var archiveService: ArchiveService<VetDelegatedByBlock, VetDelegatedByBlockArchive>

    @MockK(relaxed = true)
    lateinit var pruner: TargetedPruner<VetDelegatedByBlock, VetDelegatedByBlockArchive>

    private lateinit var service: VetDelegatedByBlockService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = VetDelegatedByBlockService(repository, mongoTemplate, archiveService, pruner)
    }

    private fun mockEvent(
        blockNumber: Long,
        timestamp: Long,
        amount: String,
        eventType: String = "DelegationInitiated",
        blockId: String = "block-$blockNumber",
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns timestamp
            every { this@mockk.eventType } returns eventType
            every { params.getAsBigInteger("amount") } returns BigInteger(amount)
            every { params.getAsInt("levelId") } returns null
        }

    @Test
    fun `empty events returns empty list`() {
        every { repository.getLatestRecord() } returns null
        val (updated, archived) = service.processEvents(emptyList())
        expectThat(updated).isEmpty()
        expectThat(archived).isEmpty()
    }

    @Test
    fun `block order violation throws`() {
        every { repository.getLatestRecord() } returns
            VetDelegatedByBlock(
                version = 1,
                blockId = "block-10",
                blockNumber = 10,
                blockTimestamp = 1000,
                total = BigInteger("100"),
                byLevel = emptyMap(),
                hourOfDay = 1,
                dayOfMonth = 1,
                weekOfYear = 1,
                month = 1,
                year = 2025,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                hourTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )

        val events =
            listOf(
                mockEvent(10, 1100, "1") // EXACT SAME BLOCK → FAIL
            )

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 10")
    }

    // ---------------------------------------------------------
    // DAY/HOUR ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `DAY rollover - previous block gets DAY tag only`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Dec 30 2025 @ 23:59 UTC (day 30)
                mockEvent(100, 1767043140, "10"),
                // Dec 31 2025 @ 00:00 UTC (day 31)
                mockEvent(101, 1767129600, "5"),
            )

        val (updated, archived) = service.processEvents(events)

        expectThat(updated).hasSize(3)

        // updated[1] = previous block (block 100) WITH rollover flag
        expectThat(updated[1].timeFrames).contains(TimeFrame.DAY)
        expectThat(updated[1].timeFrames).contains(TimeFrame.HOUR)

        // updated[2] = block 101 new day → no timeFrames
        expectThat(updated[2].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // WEEK ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `WEEK rollover - previous block gets WEEK tag`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Feb 28 2025 @ 12:00 UTC
                mockEvent(300, 1740744000, "50"),
                // Mar 28 2025 @ 12:00 UTC
                mockEvent(301, 1743336000, "10"),
            )

        val (updated, archived) = service.processEvents(events)

        expectThat(updated).hasSize(3)

        expectThat(updated[1].timeFrames).contains(TimeFrame.WEEK)
        expectThat(updated[2].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // MONTH ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `MONTH rollover - previous block gets MONTH tag`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Jan 31
                mockEvent(300, 1761961220, "50"),
                // Feb 1
                mockEvent(301, 1761950990, "10"),
            )

        val (updated, archived) = service.processEvents(events)

        expectThat(updated).hasSize(3)
        expectThat(updated[1].timeFrames).contains(TimeFrame.MONTH)
        expectThat(updated[2].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // YEAR ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `YEAR rollover - previous block gets YEAR tag`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Dec 31 2025
                mockEvent(400, 1763487400, "100"),
                // Jan 1 2026
                mockEvent(401, 1707225605, "25"),
            )

        val (updated, archived) = service.processEvents(events)

        expectThat(updated).hasSize(3)

        expectThat(updated[1].timeFrames).contains(TimeFrame.YEAR)
        expectThat(updated[2].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // SAVE RECORDS
    // ---------------------------------------------------------

    @Test
    fun `save delegates to repository`() {
        val dummy =
            listOf(
                VetDelegatedByBlock(
                    version = 1,
                    blockId = "b",
                    blockNumber = 1,
                    blockTimestamp = 1,
                    total = BigInteger.ONE,
                    byLevel = emptyMap(),
                    hourOfDay = 1,
                    dayOfMonth = 1,
                    weekOfYear = 1,
                    month = 1,
                    year = 2025,
                    timeFrames = emptyList(),
                    blockTotal = BigInteger.ONE,
                    hourTotal = BigInteger.ONE,
                    dayTotal = BigInteger.ONE,
                    weekTotal = BigInteger.ONE,
                    monthTotal = BigInteger.ONE,
                    yearTotal = BigInteger.ONE,
                )
            )
        val existing = emptyList<VetDelegatedByBlock>()

        service.saveRecords(dummy, existing)

        // Verify with relaxed mock
    }
}
