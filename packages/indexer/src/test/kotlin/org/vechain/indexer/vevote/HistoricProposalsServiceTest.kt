package org.vechain.indexer.vevote

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
internal class HistoricProposalsServiceTest {
    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: HistoricProposalsService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val props =
            TestProposalsProperties().apply {
                testProposals = mapOf("proposal1" to listOf(1, 2, 3), "proposal2" to listOf(4, 5))
            }

        service =
            HistoricProposalsService(
                thorClient = thorClient,
                repository = mockk(relaxed = true),
                steeringCommitteeAddress = "0x7e54f0790153647ec0651c35ced28171adb5d44a",
                allStakeholdersAddress = "0xa6416a72f816d3a69f33d0814700545c8e3fe4be",
                testProposalsProps = props,
            )
        coEvery { thorClient.getBlock(BlockRevision.Keyword.BEST) } returns
            mockk<Block> { every { number } returns 9999999L }
    }

    @Test
    fun `processNewProposals returns empty list for empty events`(): Unit = runBlocking {
        val result = service.processNewProposals(emptyList())
        expectThat(result.size).isEqualTo(0)
    }

    @Test
    fun `extractNewProposalEvent returns null for event with null address`(): Unit = runBlocking {
        val event = mockk<IndexedEvent> { every { address } returns null }

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNull()
    }

    @Test
    fun `extractNewProposalEvent returns null for event from invalid contract address`(): Unit =
        runBlocking {
            val event = mockk<IndexedEvent> { every { address } returns "0xinvalid" }

            val result = service.extractNewProposalEvent(event)

            expectThat(result).isNull()
        }

    @Test
    fun `extractNewProposalEvent returns null for event with null proposalId`(): Unit =
        runBlocking {
            val event =
                mockk<IndexedEvent> {
                    every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                    every { params.getReturnValues() } returns mapOf("ptype" to 1)
                }

            val result = service.extractNewProposalEvent(event)

            expectThat(result).isNull()
        }

    @Test
    fun `extractNewProposalEvent returns null when thorService throws exception`(): Unit =
        runBlocking {
            val event =
                mockk<IndexedEvent> {
                    every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "1", "ptype" to 1)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                }

            coEvery {
                thorClient.inspectClauses(any<List<Clause>>(), anyNullable<BlockRevision>())
            } throws RuntimeException("Thor error")

            val result = service.extractNewProposalEvent(event)

            expectThat(result).isNull()
        }

    @Test
    fun `extractNewProposalEvent returns proposal for valid steering committee event`(): Unit =
        runBlocking {
            val event =
                mockk<IndexedEvent> {
                    every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "1", "ptype" to 1)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                }

            coEvery {
                thorClient.inspectClauses(any<List<Clause>>(), anyNullable<BlockRevision>())
            } returns
                listOf(
                    InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
                    InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
                    InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
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
    fun `extractNewProposalEvent returns proposal for valid all stakeholders event`(): Unit =
        runBlocking {
            val event =
                mockk<IndexedEvent> {
                    every { address } returns "0xa6416a72f816d3a69f33d0814700545c8e3fe4be"
                    every { params.getReturnValues() } returns
                        mapOf("proposalId" to "2", "ptype" to 2)
                    every { blockId } returns
                        "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                    every { blockNumber } returns 4510484L
                    every { blockTimestamp } returns 1575559810L
                }

            coEvery {
                thorClient.inspectClauses(any<List<Clause>>(), anyNullable<BlockRevision>())
            } returns
                listOf(
                    InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
                    InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
                    InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
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
    fun `extractNewProposalEvent returns proposal with valid contract data`(): Unit = runBlocking {
        val event =
            mockk<IndexedEvent> {
                every { address } returns "0x7e54f0790153647ec0651c35ced28171adb5d44a"
                every { params.getReturnValues() } returns mapOf("proposalId" to "3", "ptype" to 1)
                every { blockId } returns
                    "0x0044d3147755d1818f87ef7104bf89607f98b67cd59a3d42188732378a886d0f"
                every { blockNumber } returns 4510484L
                every { blockTimestamp } returns 1575559810L
            }

        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), anyNullable<BlockRevision>())
        } returns
            listOf(
                InspectionResult(
                    "0x0000000000000000000000000000000000000000000000000000000000000001",
                    emptyList(),
                    emptyList(),
                    0,
                    false,
                    null,
                ),
                InspectionResult(
                    "0x0000000000000000000000000000000000000000000000000000000000000002",
                    emptyList(),
                    emptyList(),
                    0,
                    false,
                    null,
                ),
                InspectionResult(
                    "0x0000000000000000000000000000000000000000000000000000000000000003",
                    emptyList(),
                    emptyList(),
                    0,
                    false,
                    null,
                ),
            )

        val result = service.extractNewProposalEvent(event)

        expectThat(result).isNotNull()
        expectThat(result!!.proposalId).isEqualTo("3")
        expectThat(result!!.proposalType).isEqualTo(1)
        expectThat(result!!.id).isEqualTo("0x7e54f0790153647ec0651c35ced28171adb5d44a-3")
    }

    @Test
    fun `processNewProposals with steering committee logs`(): Unit = runBlocking {
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

        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), anyNullable<BlockRevision>())
        } returns
            listOf(
                InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
                InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
                InspectionResult("0x", emptyList(), emptyList(), 0, false, null),
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
    fun `processNewProposals with staking holder logs`(): Unit = runBlocking {
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

        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), anyNullable<BlockRevision>())
        } returns
            listOf(
                InspectionResult(
                    data = "0x",
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = false,
                    vmError = null,
                ),
                InspectionResult(
                    data = "0x",
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = false,
                    vmError = null,
                ),
                InspectionResult(
                    data = "0x",
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = false,
                    vmError = null,
                ),
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
