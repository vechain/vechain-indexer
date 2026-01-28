package org.vechain.indexer.vevote

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NO_CLAUSES
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class VeVoteResultIndexerTest {
    @MockK lateinit var veVoteProposalResultRepository: VeVoteProposalResultRepository

    @MockK lateinit var veVoteResultService: VeVoteResultService

    @MockK lateinit var indexerVersionService: IndexerVersionService

    private lateinit var voteResultsIndexer: VeVoteResultProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        voteResultsIndexer =
            VeVoteResultProcessor(
                service = veVoteResultService,
                repository = veVoteProposalResultRepository,
                indexerVersionService = indexerVersionService,
            )
    }

    @Test
    fun `process block with no vote events`() {
        runBlocking {
            voteResultsIndexer.process(
                IndexingResult.Normal(
                    events = emptyList(),
                    block = BLOCK_NO_CLAUSES,
                    callResults = emptyList(),
                    status = Status.FULLY_SYNCED,
                )
            )
        }

        verify { veVoteResultService wasNot Called }
    }
}
