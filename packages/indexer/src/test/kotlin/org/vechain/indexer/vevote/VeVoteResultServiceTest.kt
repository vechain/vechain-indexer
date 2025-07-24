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
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.vevote.Support
import org.vechain.indexer.model.vevote.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

class VeVoteResultServiceTest {
    @MockK lateinit var repository: VeVoteProposalResultRepository

    private lateinit var service: VeVoteResultService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = VeVoteResultService(repository)
    }

    @Test
    fun `aggregates a single event and inserts new result when no existing`() {
        val proposalId = "proposal-1"
        val support = BigInteger.ONE // FOR
        val weight = BigDecimal("10")

        val event = indexedEvent(proposalId, support, weight)

        every { repository.findById("proposal-1-FOR") } returns Optional.empty()

        val results = service.processVeVoteResults(listOf(event))

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals("proposal-1-FOR", result.id)
        assertEquals(Support.FOR, result.support)
        assertEquals(BigDecimal("10"), result.totalWeight)
        assertEquals(1, result.totalVoters)
    }

    @Test
    fun `merges with existing result if already stored`() {
        val proposalId = "proposal-2"
        val support = BigInteger.ZERO // AGAINST
        val weight = BigDecimal("4.5")
        val existingWeight = BigDecimal("5.5")

        val event = indexedEvent(proposalId, support, weight)
        val existingResult =
            VeVoteProposalResults(
                id = "proposal-2-AGAINST",
                blockId = "old-block",
                blockNumber = 10,
                blockTimestamp = Instant.now().epochSecond,
                proposalId = proposalId,
                support = Support.AGAINST,
                totalWeight = existingWeight,
                totalVoters = 2,
            )

        every { repository.findById("proposal-2-AGAINST") } returns Optional.of(existingResult)

        val results = service.processVeVoteResults(listOf(event))

        assertEquals(1, results.size)
        val merged = results.first()
        assertEquals(Support.AGAINST, merged.support)
        assertEquals(existingWeight + weight, merged.totalWeight)
        assertEquals(3, merged.totalVoters)
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
