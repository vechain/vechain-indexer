package org.vechain.indexer.historical

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
internal class HistoricalProposalsServiceTest {

    @MockK lateinit var thorService: ThorService

    private lateinit var service: HistoricalProposalsService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            HistoricalProposalsService(
                thorService = thorService,
                steeringCommitteeAddress = "0x7e54f0790153647ec0651c35ced28171adb5d44a",
                allStakeholdersAddress = "0xa6416a72f816d3a69f33d0814700545c8e3fe4be",
            )
        every { thorService.getBestBlock() } returns mockk { every { number } returns 9999999L }
    }

    @Test
    fun `processNewProposals returns empty list for empty events`() {
        val result = service.processNewProposals(emptyList())
        expectThat(result.size).isEqualTo(0)
    }

    @Test
    fun `extractNewProposalEvent returns null for event with null address`() {
        val event = mockk<IndexedEvent> { every { address } returns null }

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNull()
    }

    @Test
    fun `extractNewProposalEvent returns null for event from invalid contract address`() {
        val event = mockk<IndexedEvent> { every { address } returns "0xinvalid" }

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNull()
    }

    @Test
    fun `extractNewProposalEvent returns null for event with null proposalId`() {
        val event =
            mockk<IndexedEvent> {
                every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                every { params.getReturnValues() } returns mapOf("ptype" to 1)
            }

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNull()
    }

    @Test
    fun `extractNewProposalEvent returns null when thorService throws exception`() {
        val event =
            mockk<IndexedEvent> {
                every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                every { params.getReturnValues() } returns mapOf("proposalId" to "1", "ptype" to 1)
                every { blockId } returns
                    "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                every { blockNumber } returns 4510484L
                every { blockTimestamp } returns 1575559810L
            }

        every { thorService.executeReadOnlyCode(any()) } throws RuntimeException("Thor error")

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNull()
    }

    @Test
    fun `extractNewProposalEvent returns proposal for valid steering committee event`() {
        val event =
            mockk<IndexedEvent> {
                every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                every { params.getReturnValues() } returns mapOf("proposalId" to "1", "ptype" to 1)
                every { blockId } returns
                    "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                every { blockNumber } returns 4510484L
                every { blockTimestamp } returns 1575559810L
            }

        // Mock thorService to return "0x" for most responses (triggers isNullOrBlank() check)
        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
            )

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNotNull()
        expectThat(result!!.proposalId).isEqualTo("1")
        expectThat(result!!.proposalType).isEqualTo(1)
        expectThat(result!!.id).isEqualTo("0x7e54f0790153647ec0651c35ced28171adb5d44a-1")
        expectThat(result!!.title).isNull()
        expectThat(result!!.choices).isNull()
        expectThat(result!!.createTime).isNull()
        expectThat(result!!.votingStartTime).isNull()
        expectThat(result!!.votingEndTime).isNull()
        expectThat(result!!.voteTallies).isNull()
        expectThat(result!!.totalVotes).isEqualTo(0L)
    }

    @Test
    fun `extractNewProposalEvent returns proposal for valid all stakeholders event`() {
        val event =
            mockk<IndexedEvent> {
                every { address } returns "0xa6416a72f816d3a69f33d0814700545c8e3fe4be"
                every { params.getReturnValues() } returns mapOf("proposalId" to "2", "ptype" to 2)
                every { blockId } returns
                    "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                every { blockNumber } returns 4510484L
                every { blockTimestamp } returns 1575559810L
            }

        // Mock thorService to return "0x" for most responses (triggers isNullOrBlank() check)
        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
            )

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNotNull()
        expectThat(result!!.proposalId).isEqualTo("2")
        expectThat(result!!.proposalType).isEqualTo(2)
        expectThat(result!!.id).isEqualTo("0xa6416a72f816d3a69f33d0814700545c8e3fe4be-2")
        expectThat(result!!.title).isNull()
        expectThat(result!!.choices).isNull()
        expectThat(result!!.createTime).isNull()
        expectThat(result!!.votingStartTime).isNull()
        expectThat(result!!.votingEndTime).isNull()
        expectThat(result!!.voteTallies).isNull()
        expectThat(result!!.totalVotes).isEqualTo(0L)
    }

    @Test
    fun `extractNewProposalEvent returns proposal with valid contract data`() {
        val event =
            mockk<IndexedEvent> {
                every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                every { params.getReturnValues() } returns mapOf("proposalId" to "3", "ptype" to 1)
                every { blockId } returns
                    "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                every { blockNumber } returns 4510484L
                every { blockTimestamp } returns 1575559810L
            }

        // Mock thorService to return valid hex data from the logs
        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                mockk {
                    every { data } returns
                        "0x0000000000000000000000000000000000000000000000000000000000000001"
                },
                mockk {
                    every { data } returns
                        "0x0000000000000000000000000000000000000000000000000000000000000002"
                },
                mockk {
                    every { data } returns
                        "0x0000000000000000000000000000000000000000000000000000000000000003"
                },
            )

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNotNull()
        expectThat(result!!.proposalId).isEqualTo("3")
        expectThat(result!!.proposalType).isEqualTo(1)
        expectThat(result!!.id).isEqualTo("0x7e54f0790153647ec0651c35ced28171adb5d44a-3")
    }

    @Test
    fun `processNewProposals with steering committee logs`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "1", "ptype" to 1)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                },
                mockk<IndexedEvent> {
                    every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "2", "ptype" to 1)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                },
                mockk<IndexedEvent> {
                    every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "3", "ptype" to 1)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                },
            )

        // Mock thorService to return "0x" for most responses (triggers isNullOrBlank() check)
        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
            )

        val result = service.processNewProposals(events)

        expectThat(result.size).isEqualTo(3)
        expectThat(result[0].proposalId).isEqualTo("1")
        expectThat(result[0].proposalType).isEqualTo(1)
        expectThat(result[0].id).isEqualTo("0x7e54f0790153647ec0651c35ced28171adb5d44a-1")
        expectThat(result[1].proposalId).isEqualTo("2")
        expectThat(result[1].proposalType).isEqualTo(1)
        expectThat(result[1].id).isEqualTo("0x7e54f0790153647ec0651c35ced28171adb5d44a-2")
        expectThat(result[2].proposalId).isEqualTo("3")
        expectThat(result[2].proposalType).isEqualTo(1)
        expectThat(result[2].id).isEqualTo("0x7e54f0790153647ec0651c35ced28171adb5d44a-3")
    }

    @Test
    fun `processNewProposals with staking holder logs`() {
        val events =
            listOf(
                mockk<IndexedEvent> {
                    every { address } returns "0xa6416a72f816d3a69f33d0814700545c8e3fe4be"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "1", "ptype" to 2)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                },
                mockk<IndexedEvent> {
                    every { address } returns "0xa6416a72f816d3a69f33d0814700545c8e3fe4be"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "2", "ptype" to 2)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                },
                mockk<IndexedEvent> {
                    every { address } returns "0xa6416a72f816d3a69f33d0814700545c8e3fe4be"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "3", "ptype" to 2)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                },
            )

        // Mock thorService to return "0x" for most responses (triggers isNullOrBlank() check)
        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
            )

        val result = service.processNewProposals(events)

        expectThat(result.size).isEqualTo(3)
        expectThat(result[0].proposalId).isEqualTo("1")
        expectThat(result[0].proposalType).isEqualTo(2)
        expectThat(result[0].id).isEqualTo("0xa6416a72f816d3a69f33d0814700545c8e3fe4be-1")
        expectThat(result[1].proposalId).isEqualTo("2")
        expectThat(result[1].proposalType).isEqualTo(2)
        expectThat(result[1].id).isEqualTo("0xa6416a72f816d3a69f33d0814700545c8e3fe4be-2")
        expectThat(result[2].proposalId).isEqualTo("3")
        expectThat(result[2].proposalType).isEqualTo(2)
        expectThat(result[2].id).isEqualTo("0xa6416a72f816d3a69f33d0814700545c8e3fe4be-3")
    }
}
