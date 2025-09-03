package org.vechain.indexer.vevote

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@ExtendWith(MockKExtension::class)
internal class VeVoteResultServiceProcessTest {
    @MockK lateinit var repository: VeVoteProposalResultRepository

    @MockK
    lateinit var archiveService: ArchiveService<VeVoteProposalResults, VeVoteProposalResultsArchive>

    private lateinit var service: VeVoteResultService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = VeVoteResultService(repository, archiveService)
    }

    @Test
    fun `process saves new result when none exists`() {
        val event = indexedEvent("proposal-1", BigInteger.ONE, BigDecimal("10")) // FOR

        // findAllById is used in process()
        every { repository.findAllById(setOf("proposal-1-FOR")) } returns emptyList()

        // capture what gets saved
        val savedSlot = slot<Iterable<VeVoteProposalResults>>()
        every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured } // echo back

        // archive should not be called when nothing existed
        every { archiveService.saveAll(any<List<VeVoteProposalResults>>()) } just Runs

        val results = service.processVeVoteResults(listOf(event))
        service.save(results.first, results.second)

        // Assert saved contents
        val saved = savedSlot.captured.toList()
        assertEquals(1, saved.size)
        val r = saved.first()
        assertEquals("proposal-1-FOR", r.id)
        assertEquals(Support.FOR, r.support)
        assertEquals(BigDecimal("10"), r.totalWeight)
        assertEquals(1, r.totalVoters)
        assertEquals(1, r.version)

        // Verify interactions
        verify(exactly = 1) { repository.findAllById(setOf("proposal-1-FOR")) }
        verify(exactly = 1) { repository.saveAll(any<Iterable<VeVoteProposalResults>>()) }
        verify(exactly = 0) { archiveService.saveAll(any<List<VeVoteProposalResults>>()) }
    }

    @Test
    fun `process merges with existing and archives old`() {
        val event = indexedEvent("proposal-2", BigInteger.ZERO, BigDecimal("4.5")) // AGAINST
        val existing =
            VeVoteProposalResults(
                id = "proposal-2-AGAINST",
                blockId = "old-block",
                blockNumber = 10,
                blockTimestamp = Instant.now().epochSecond,
                proposalId = "proposal-2",
                support = Support.AGAINST,
                totalWeight = BigDecimal("5.5"),
                totalVoters = 2,
                version = 1,
            )

        every { repository.findAllById(setOf("proposal-2-AGAINST")) } returns listOf(existing)

        val savedSlot = slot<Iterable<VeVoteProposalResults>>()
        every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured }

        val archivedSlot = slot<List<VeVoteProposalResults>>()
        every { archiveService.saveAll(capture(archivedSlot)) } just Runs

        val results = service.processVeVoteResults(listOf(event))
        service.save(results.first, results.second)

        val saved = savedSlot.captured.toList()
        assertEquals(1, saved.size)
        val merged = saved.first()

        // totals merged
        assertEquals(existing.totalWeight.add(BigDecimal("4.5")), merged.totalWeight)
        assertEquals(existing.totalVoters + 1, merged.totalVoters)
        // version bumped
        assertEquals(existing.version + 1, merged.version)
        // id & support unchanged
        assertEquals("proposal-2-AGAINST", merged.id)
        assertEquals(Support.AGAINST, merged.support)

        // archived the old row(s)
        assertEquals(listOf(existing), archivedSlot.captured)

        verify(exactly = 1) { repository.findAllById(setOf("proposal-2-AGAINST")) }
        verify(exactly = 1) { repository.saveAll(any<Iterable<VeVoteProposalResults>>()) }
        verify(exactly = 1) { archiveService.saveAll(any<List<VeVoteProposalResults>>()) }
    }

    // helper from your earlier test
    private fun indexedEvent(
        proposalId: String,
        support: BigInteger,
        weight: BigDecimal,
        blockId: String = "block-123",
        blockNumber: Long = 42,
        blockTimestamp: Long = Instant.now().epochSecond,
    ): IndexedEvent {
        val params =
            mockk<AbiEventParameters> {
                every { getAsString("proposalId") } returns proposalId
                every { getAsBigInteger("support") } returns support
                every { getAsBigDecimal("weight") } returns weight
            }
        return IndexedEvent(
            id = UUID.randomUUID().toString(),
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            txId = "0xtx",
            origin = null,
            paid = null,
            gasUsed = null,
            gasPayer = null,
            raw = null,
            params = params,
            address = null,
            eventType = "VoteCast",
            clauseIndex = 0,
            signature = null,
        )
    }
}
