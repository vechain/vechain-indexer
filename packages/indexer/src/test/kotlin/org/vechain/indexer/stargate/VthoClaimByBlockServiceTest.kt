package org.vechain.indexer.stargate

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.stargate.VthoClaimedByBlock
import org.vechain.indexer.repository.stargate.VthoClaimedByBlockRepository
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

@ExtendWith(MockKExtension::class)
internal class VthoClaimByBlockServiceTest {

    @MockK lateinit var repository: VthoClaimedByBlockRepository

    private lateinit var service: VthoClaimedByBlockService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = VthoClaimedByBlockService(repository)
    }

    @Test
    fun `processEvents returns null for empty events`() {
        val result = service.processEvents(emptyList())
        expect { that(result).isNull() }
    }

    @Test
    fun `processEvents returns new record if no previous record exists`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block1"
                    every { blockNumber } returns 10L
                    every { blockTimestamp } returns 1000L
                    every { params.getAsBigInteger("value") } returns BigInteger("100")
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block2"
                    every { blockNumber } returns 12L
                    every { blockTimestamp } returns 1200L
                    every { params.getAsBigInteger("value") } returns BigInteger("200")
                },
            )
        every { repository.getLatestRecord() } returns null

        val result = service.processEvents(events)

        expect {
            that(result?.blockId).isEqualTo("block2")
            that(result?.blockNumber).isEqualTo(12L)
            that(result?.blockTimestamp).isEqualTo(1200L)
            that(result?.total).isEqualTo(BigInteger("300"))
        }
    }

    @Test
    fun `processEvents adds to previous total if previous record exists`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block3"
                    every { blockNumber } returns 15L
                    every { blockTimestamp } returns 1500L
                    every { params.getAsBigInteger("value") } returns BigInteger("50")
                }
            )
        val latestRecord =
            VthoClaimedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
            )
        every { repository.getLatestRecord() } returns latestRecord

        val result = service.processEvents(events)

        expect {
            that(result?.blockId).isEqualTo("block3")
            that(result?.blockNumber).isEqualTo(15L)
            that(result?.blockTimestamp).isEqualTo(1500L)
            that(result?.total).isEqualTo(BigInteger("350"))
        }
    }

    @Test
    fun `processEvents throws if latest record block number is greater or equal`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block3"
                    every { blockNumber } returns 12L
                    every { blockTimestamp } returns 1500L
                    every { params.getAsBigInteger("value") } returns BigInteger("50")
                }
            )
        val latestRecord =
            VthoClaimedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
            )
        every { repository.getLatestRecord() } returns latestRecord

        try {
            service.processEvents(events)
        } catch (e: IllegalStateException) {
            expect {
                that(e.message)
                    .isEqualTo(
                        "Latest record block number 12 is greater than or equal to the latest event block number 12"
                    )
            }
        }
    }

    @Test
    fun `saveRecord delegates to repository`() {
        val record =
            VthoClaimedByBlock(
                blockId = "blockX",
                blockNumber = 99L,
                blockTimestamp = 9999L,
                total = BigInteger("123"),
            )
        every { repository.save(record) } returns record

        service.saveRecord(record)

        verify(exactly = 1) { repository.save(record) }
    }
}
