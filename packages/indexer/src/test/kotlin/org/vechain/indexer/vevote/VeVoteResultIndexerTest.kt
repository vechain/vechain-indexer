package org.vechain.indexer.vevote

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NO_CLAUSES

@ExtendWith(MockKExtension::class)
class VeVoteResultIndexerTest {
    @MockK lateinit var veVoteProposalResultRepository: VeVoteProposalResultRepository

    @MockK lateinit var veVoteResultService: VeVoteResultService

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var voteResultsIndexer: VeVoteResultProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        voteResultsIndexer =
            VeVoteResultProcessor(
                service = veVoteResultService,
                repository = veVoteProposalResultRepository,
            )
    }

    @Test
    fun `process block with no vote events`() {
        voteResultsIndexer.process(emptyList(), BLOCK_NO_CLAUSES)

        verify { mongoTemplate wasNot Called }
    }
}
