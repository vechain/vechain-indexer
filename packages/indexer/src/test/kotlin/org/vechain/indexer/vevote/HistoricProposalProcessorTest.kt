package org.vechain.indexer.vevote

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.LogsFixtures
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.version.IndexerVersionService
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isNotNull

@ExtendWith(MockKExtension::class)
class HistoricProposalsProcessorTest {
    @MockK lateinit var thorService: ThorService
    @MockK private lateinit var historicProposalsService: HistoricProposalsService
    @MockK lateinit var indexerVersionService: IndexerVersionService
    private val repository = mockk<HistoricProposalsRepository>(relaxed = true)
    lateinit var processor: HistoricProposalsProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { thorService.getBestBlock() } returns mockk { every { number } returns 0L }
        processor =
            HistoricProposalsProcessor(repository, historicProposalsService, indexerVersionService)
    }

    @Test
    fun `process with Steering committee NewProposal Event`() = runBlocking {
        val events = createIndexedEvents(LogsFixtures.LOG_STEERING_COMMITTEE.take(3))

        val proposalsSlot = slot<List<HistoricProposals>>()
        every { repository.getLatestRecord() } returns null
        every { repository.saveAll(capture(proposalsSlot)) } returns emptyList()

        // Mock thorService responses for contract calls
        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
            )

        every {
            historicProposalsService.processNewProposals(any<List<IndexedEvent>>(), any())
        } returns
            events.map { event ->
                mockk<HistoricProposals> {
                    every { proposalType } returns 1
                    every { id } returns "${event.address}-1"
                }
            }
        processor.process(
            IndexingResult.EventsOnly(
                events = events,
                endBlock = events.maxBy { it.blockNumber }.blockNumber,
            )
        )
        val proposals = proposalsSlot.captured
        expectThat(proposals).hasSize(3)
        proposals.forEach { proposal ->
            expectThat(proposal.proposalType).isNotNull()
            expectThat(proposal.id).contains("0x7e54f0790153647ec0651c35ced28171adb5d44a")
        }
    }

    @Test
    fun `process with All Stakeholders NewProposal Event`() = runBlocking {
        val events = createIndexedEvents(LogsFixtures.LOG_ALL_STAKING_HOLDER.take(2))

        val proposalsSlot = slot<List<HistoricProposals>>()
        every { repository.getLatestRecord() } returns null
        every { repository.saveAll(capture(proposalsSlot)) } returns emptyList()

        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
                mockk { every { data } returns "0x" },
            )

        val mockProposals =
            events.map { event ->
                mockk<HistoricProposals> {
                    every { proposalType } returns 1
                    every { id } returns "${event.address}-1"
                }
            }
        every {
            historicProposalsService.processNewProposals(any<List<IndexedEvent>>(), any())
        } returns mockProposals

        processor.process(
            IndexingResult.EventsOnly(
                events = events,
                endBlock = events.maxBy { it.blockNumber }.blockNumber,
            )
        )
        val proposals = proposalsSlot.captured
        expectThat(proposals).hasSize(2)
        proposals.forEach { proposal ->
            expectThat(proposal.proposalType).isNotNull()
            expectThat(proposal.id).contains("0xa6416a72f816d3a69f33d0814700545c8e3fe4be")
        }
    }

    private fun createIndexedEvents(logs: List<EventLog>): List<IndexedEvent> {
        return logs.map { log ->
            // Extract values from topics
            val proposalId =
                if (log.topics.size > 1) {
                    log.topics[1].removePrefix("0x").toBigInteger(16).toString()
                } else {
                    throw IllegalArgumentException(
                        "Log missing proposalId in topics[1]: ${log.topics}"
                    )
                }

            val ptype =
                if (log.topics.size > 2) {
                    log.topics[2].removePrefix("0x").toBigInteger(16).toLong().toInt()
                } else {
                    throw IllegalArgumentException("Log missing ptype in topics[2]: ${log.topics}")
                }

            mockk<IndexedEvent> {
                every { address } returns log.address
                every { params } returns
                    mockk {
                        every { getReturnValues() } returns
                            mapOf("proposalId" to proposalId, "ptype" to ptype)
                    }
                every { blockId } returns log.meta.blockID
                every { blockNumber } returns log.meta.blockNumber
                every { blockTimestamp } returns log.meta.blockTimestamp
            }
        }
    }
}
