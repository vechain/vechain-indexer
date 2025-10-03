package org.vechain.indexer.amn

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class AmnProcessorTest {
    @MockK lateinit var amnRepository: AmnRepository

    @MockK lateinit var amnService: AmnService

    @MockK lateinit var thorService: ThorService

    @MockK lateinit var indexerVersionService: IndexerVersionService

    private lateinit var processor: AmnProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        processor =
            AmnProcessor(
                repository = amnRepository,
                amnService = amnService,
                thorService = thorService,
                indexerVersionService = indexerVersionService,
            )
    }

    @Test
    fun `getLastSyncedBlock - returns best block if DB is empty`() {
        every { amnRepository.count() } returns 0L
        every { thorService.getBestBlock() } returns BlockFixtures.BLOCK_MP_SALES

        val result = processor.getLastSyncedBlock()

        assert(result!!.number == BlockFixtures.BLOCK_MP_SALES.number)
        verify { thorService.getBestBlock() }
    }

    @Test
    fun `getLastSyncedBlock - delegates to super if DB is not empty`() {
        every { amnRepository.count() } returns 5L
        val superResult = AmnEndorser("0xabc", 50, blockTimestamp = 123L, blockId = "a")
        every { amnRepository.getLatestRecord() } returns superResult

        val result = processor.getLastSyncedBlock()

        assert(result!!.number == superResult.blockNumber)
    }

    @Test
    fun `process - calls sync and process when DB is empty`() {
        every { amnRepository.count() } returns 0L
        every { amnService.syncEndorsersForAllNodes() } just Runs
        every { amnService.processCandidateEvents(any()) } just Runs

        processor.process(IndexingResult.EventsOnly(100, emptyList()))

        verify { amnService.syncEndorsersForAllNodes() }
        verify { amnService.processCandidateEvents(any()) }
    }

    @Test
    fun `process - skips sync when already synced`() {
        every { amnRepository.count() } returnsMany listOf(0L, 1L)
        every { amnService.syncEndorsersForAllNodes() } just Runs
        every { amnService.processCandidateEvents(any()) } just Runs

        processor.process(IndexingResult.EventsOnly(100, emptyList()))
        processor.process(IndexingResult.EventsOnly(100, emptyList()))

        verify(exactly = 1) { amnService.syncEndorsersForAllNodes() }
    }
}
