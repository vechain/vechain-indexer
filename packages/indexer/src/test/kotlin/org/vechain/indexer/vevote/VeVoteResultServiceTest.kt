package org.vechain.indexer.vevote

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

class VeVoteResultServiceTest {
    private val repository = mockk<VeVoteWriteRepository>()
    private lateinit var service: VeVoteResultService

    private val proposal = "1".repeat(65)
    private val other = "2".repeat(65)

    @BeforeEach
    fun setup() {
        every { repository.findCurrentResults(any()) } returns emptyList()
        service = VeVoteResultService(repository)
    }

    @Test
    fun `a first vote opens the proposal's tally for that support`() {
        val result = service.processEvents(listOf(vote(proposal, FOR, "10"))).single()

        assertEquals(proposal, result.proposalId)
        assertEquals(Support.FOR, result.support)
        assertEquals(BigDecimal("10"), result.totalWeight)
        assertEquals(1, result.totalVoters)
        assertEquals(42L, result.blockNumber)
    }

    @Test
    fun `a vote adds to the current tally`() {
        every { repository.findCurrentResults(any()) } returns
            listOf(current(proposal, Support.AGAINST, "15", voters = 3))

        val result = service.processEvents(listOf(vote(proposal, AGAINST, "5"))).single()

        assertEquals(BigDecimal("20"), result.totalWeight)
        assertEquals(4, result.totalVoters)
    }

    @Test
    fun `two votes in one block produce one row`() {
        every { repository.findCurrentResults(any()) } returns
            listOf(current(proposal, Support.ABSTAIN, "10", voters = 2))

        val result =
            service
                .processEvents(
                    listOf(vote(proposal, ABSTAIN, "3", 50), vote(proposal, ABSTAIN, "7", 50))
                )
                .single()

        assertEquals(BigDecimal("20"), result.totalWeight)
        assertEquals(4, result.totalVoters)
        assertEquals(50L, result.blockNumber)
    }

    @Test
    fun `two blocks produce a row each, in ascending block order`() {
        val results =
            service.processEvents(
                listOf(vote(proposal, FOR, "6", 61), vote(proposal, FOR, "4", 60))
            )

        assertEquals(listOf(60L, 61L), results.map { it.blockNumber })
        assertEquals(listOf(BigDecimal("4"), BigDecimal("10")), results.map { it.totalWeight })
        assertEquals(listOf(1, 2), results.map { it.totalVoters })
    }

    @Test
    fun `each support and each proposal keeps its own tally`() {
        val results =
            service.processEvents(
                listOf(
                    vote(proposal, FOR, "1"),
                    vote(proposal, AGAINST, "2"),
                    vote(other, FOR, "3"),
                )
            )

        assertEquals(3, results.size)
        assertEquals(
            setOf(proposal to Support.FOR, proposal to Support.AGAINST, other to Support.FOR),
            results.map { it.proposalId to it.support }.toSet(),
        )
        assertEquals(listOf(1, 1, 1), results.map { it.totalVoters })
    }

    private fun current(
        proposalId: String,
        support: Support,
        weight: String,
        voters: Int,
    ): VeVoteProposalResult =
        VeVoteProposalResult(
            blockId = "0x01",
            blockNumber = 40,
            blockTimestamp = 1_000,
            proposalId = proposalId,
            support = support,
            totalWeight = BigDecimal(weight),
            totalVoters = voters,
        )

    private fun vote(
        proposalId: String,
        support: BigInteger,
        weight: String,
        blockNumber: Long = 42,
    ): IndexedEvent {
        val params =
            mockk<AbiEventParameters> {
                every { getAsString("proposalId") } returns proposalId
                every { getAsBigInteger("support") } returns support
                every { getAsBigDecimal("weight") } returns BigDecimal(weight)
            }

        return IndexedEvent(
            id = UUID.randomUUID().toString(),
            blockId = "0x0$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
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

    private companion object {
        val AGAINST: BigInteger = BigInteger.ZERO
        val FOR: BigInteger = BigInteger.ONE
        val ABSTAIN: BigInteger = BigInteger.TWO
    }
}
