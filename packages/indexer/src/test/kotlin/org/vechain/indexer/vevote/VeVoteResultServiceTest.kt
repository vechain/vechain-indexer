package org.vechain.indexer.vevote

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

class VeVoteResultServiceTest {
    @MockK lateinit var repository: VeVoteProposalResultRepository

    @MockK
    lateinit var veVoteProposalResultArchive:
        ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>

    private lateinit var service: VeVoteResultService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = VeVoteResultService(repository, veVoteProposalResultArchive)
    }

    @Test
    fun `aggregates a single event and inserts new result when no existing`() {
        val proposalId = "proposal-1"
        val support = BigInteger.ONE // FOR
        val weight = BigDecimal("10")

        val event = indexedEvent(proposalId, support, weight)

        every { repository.findById("proposal-1-FOR") } returns Optional.empty()

        val (updated, existing) = service.processEvents(listOf(event))

        assertEquals(1, updated.size)
        val result = updated.first()
        assertEquals("proposal-1-FOR", result.id)
        assertEquals(Support.FOR, result.support)
        assertEquals(BigDecimal("10"), result.totalWeight)
        assertEquals(1, result.totalVoters)
    }

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
