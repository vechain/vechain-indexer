package org.vechain.indexer.vevote

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NO_CLAUSES

@ExtendWith(MockKExtension::class)
class VeVoteCommentProcessorTest {
    @MockK lateinit var vevoteCommentRepository: VevoteCommentRepository

    @MockK lateinit var veVoteCommentService: VeVoteCommentService

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var checkpointService: CheckpointService

    private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

    private lateinit var vevoteCommentProcessor: VeVoteCommentProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        vevoteCommentProcessor =
            VeVoteCommentProcessor(
                vevoteCommentRepository,
                veVoteCommentService,
                mongoTemplate,
                checkpointService,
                processorMetrics,
            )
    }

    @Test
    fun `process block with no comment events`() {
        runBlocking {
            vevoteCommentProcessor.process(
                IndexingResult.Normal(
                    events = emptyList(),
                    block = BLOCK_NO_CLAUSES,
                    callResults = emptyList(),
                    status = Status.FULLY_SYNCED,
                )
            )
        }

        verify { mongoTemplate wasNot Called }
    }
}
