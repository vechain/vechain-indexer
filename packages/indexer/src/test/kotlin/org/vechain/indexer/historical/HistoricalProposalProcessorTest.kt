package org.vechain.indexer.historical

import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures

@ExtendWith(MockKExtension::class)
class HistoricalProposalsProcessorTest {
    @MockK lateinit var repository: HistoricalProposalsRepository

    @MockK lateinit var service: HistoricalProposalsService

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var processor: HistoricalProposalsProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        processor = HistoricalProposalsProcessor(repository, mongoTemplate, service)
    }

    @Test
    fun `process - if no events then service and repository should not be called`() {
        processor.process(emptyList(), BlockFixtures.BLOCK_NO_CLAUSES)

        verify { service wasNot Called }
        verify { repository wasNot Called }
    }

    @Test
    fun `process - if events are present and service returns proposals then repository saveAll should be called`() {
        val events = listOf(mockk<IndexedEvent>())
        val proposals = listOf(mockk<HistoricalProposals>())

        every { service.processNewProposals(events) } returns proposals
        every { repository.saveAll(proposals) } returns proposals

        processor.process(events, BlockFixtures.BLOCK_SINGLE_CLAUSE)

        verify { service.processNewProposals(events) }
        verify { repository.saveAll(proposals) }
    }

    @Test
    fun `process - if events are present but service returns empty list then repository saveAll should not be called`() {
        val events = listOf(mockk<IndexedEvent>())

        every { service.processNewProposals(events) } returns emptyList()

        processor.process(events, BlockFixtures.BLOCK_SINGLE_CLAUSE)

        verify { service.processNewProposals(events) }
        verify { repository wasNot Called }
    }
}
