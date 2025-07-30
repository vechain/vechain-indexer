package org.vechain.indexer.stargate

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsInt
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

@ExtendWith(MockKExtension::class)
internal class NftHolderByBlockServiceTest {
    @MockK lateinit var repository: NftHoldersByBlockRepository

    private lateinit var service: NftHoldersByBlockService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = NftHoldersByBlockService(repository)
    }

    @Test
    fun `processEvents returns null for empty events`() {
        val result = service.processEvents(emptyList())
        expect { that(result).isNull() }
    }

    @Test
    fun `processEvents calculates holders and byLevel correctly`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block1"
                    every { blockNumber } returns 10L
                    every { blockTimestamp } returns 1000L
                    every { eventType } returns "STARGATE_STAKE"
                    every { params.getAsInt("levelId") } returns 1
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block2"
                    every { blockNumber } returns 12L
                    every { blockTimestamp } returns 1200L
                    every { eventType } returns "STARGATE_STAKE"
                    every { params.getAsInt("levelId") } returns 2
                },
                mockk<IndexedEvent> {
                    every { blockId } returns "block3"
                    every { blockNumber } returns 13L
                    every { blockTimestamp } returns 1300L
                    every { eventType } returns "STARGATE_UNSTAKE"
                    every { params.getAsInt("levelId") } returns 1
                },
            )
        val latestRecord =
            NftHoldersByBlock(
                blockId = "block0",
                blockNumber = 9L,
                blockTimestamp = 900L,
                total = 5L,
                byLevel = mutableMapOf(TokenLevel.Strength to 2L, TokenLevel.Thunder to 1L),
            )
        every { repository.getLatestRecord() } returns latestRecord

        val result = service.processEvents(events)

        expect {
            that(result?.blockId).isEqualTo("block3")
            that(result?.blockNumber).isEqualTo(13L)
            that(result?.blockTimestamp).isEqualTo(1300L)
            that(result?.total).isEqualTo(6L)
            that(result?.byLevel?.get(TokenLevel.Strength)).isEqualTo(2L)
            that(result?.byLevel?.get(TokenLevel.Thunder)).isEqualTo(2L)
        }
    }

    @Test
    fun `processEvents throws if levelId missing`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { blockId } returns "block1"
                    every { blockNumber } returns 10L
                    every { blockTimestamp } returns 1000L
                    every { eventType } returns "STARGATE_STAKE"
                    every { params.getAsInt("levelId") } returns null
                }
            )
        every { repository.getLatestRecord() } returns null

        try {
            service.processEvents(events)
        } catch (e: IllegalArgumentException) {
            expect { that(e.message).isEqualTo("Missing levelId in event params") }
        }
    }

    @Test
    fun `saveRecord delegates to repository`() {
        val record =
            NftHoldersByBlock(
                blockId = "blockX",
                blockNumber = 99L,
                blockTimestamp = 9999L,
                total = 1L,
                byLevel = mutableMapOf(TokenLevel.Dawn to 1L),
            )
        every { repository.save(record) } returns record

        service.saveRecord(record)

        verify(exactly = 1) { repository.save(record) }
    }
}
