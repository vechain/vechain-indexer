package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigDecimal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NO_CLAUSES
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_VEVOTE_RESULTS
import org.vechain.indexer.model.vevote.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository
import org.vechain.indexer.service.VeVoteResultService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.FileUtils
import org.vechain.indexer.vevote.VeVoteResultIndexer
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
class VeVoteResultIndexerTest {
    @MockK lateinit var veVoteProposalResultRepository: VeVoteProposalResultRepository

    @MockK lateinit var veVoteResultService: VeVoteResultService

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var voteResultsIndexer: VeVoteResultIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val abiFileStreams = FileUtils.loadFileStreams("test-abis")
        val abiManager = AbiManager()
        abiManager.loadAbis(abiFileStreams)

        voteResultsIndexer =
            VeVoteResultIndexer(
                thorClient = DefaultThorClient("http://localhost:8669"),
                abiManager = abiManager,
                service = veVoteResultService,
                veVoteProposalResultRepository = veVoteProposalResultRepository,
                startBlock = 0L,
                syncLogInterval = 1000L,
                contractAddress = "0xfcc8f0d6ef2eef8d6fcf376ecf42d7851171a5cc",
                syncBlockBatchSize = 1000L,
            )
    }

    @Test
    fun `process block with no vote events`() {
        voteResultsIndexer.processBlock(BLOCK_NO_CLAUSES)

        verify { mongoTemplate wasNot Called }
    }

    @Test
    fun `can aggregate votes by choice`() {
        val resultSlot = slot<Collection<VeVoteProposalResults>>()

        every { veVoteProposalResultRepository.saveAll(capture(resultSlot)) } returns emptyList()

        every { veVoteResultService.processVeVoteResults(any()) } answers
            {
                listOf(
                    VeVoteProposalResults(
                        id = "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7-1",
                        blockId = LOGS_VEVOTE_RESULTS[1].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[1].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[1].meta.blockTimestamp,
                        proposalId =
                            "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7",
                        choice = 1,
                        totalWeight = BigDecimal("500"),
                        totalVoters = 3,
                    ),
                    VeVoteProposalResults(
                        id = "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7-2",
                        blockId = LOGS_VEVOTE_RESULTS[2].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[2].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[2].meta.blockTimestamp,
                        proposalId =
                            "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7",
                        choice = 2,
                        totalWeight = BigDecimal("1500"),
                        totalVoters = 2,
                    ),
                    VeVoteProposalResults(
                        id = "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7-4",
                        blockId = LOGS_VEVOTE_RESULTS[3].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[3].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[3].meta.blockTimestamp,
                        proposalId =
                            "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7",
                        choice = 4,
                        totalWeight = BigDecimal("3000"),
                        totalVoters = 2,
                    ),
                )
            }

        // Process logs
        voteResultsIndexer.processLogs(LOGS_VEVOTE_RESULTS, emptyList())

        // Verify results
        val capturedResults = resultSlot.captured

        expect {
            that(capturedResults).hasSize(3)
            // Check that we have the expected choices
            expectThat(capturedResults.map { it.choice }.sorted()).isEqualTo(listOf(1, 2, 4))

            // Check vote counts for each choice
            expectThat(capturedResults.first { it.choice == 1 }.totalVoters).isEqualTo(3)
            expectThat(capturedResults.first { it.choice == 2 }.totalVoters).isEqualTo(2)
            expectThat(capturedResults.first { it.choice == 4 }.totalVoters).isEqualTo(2)

            // Check total weights for each choice
            expectThat(capturedResults.first { it.choice == 1 }.totalWeight)
                .isEqualTo(BigDecimal("500"))
            expectThat(capturedResults.first { it.choice == 2 }.totalWeight)
                .isEqualTo(BigDecimal("1500"))
            expectThat(capturedResults.first { it.choice == 4 }.totalWeight)
                .isEqualTo(BigDecimal("3000"))
        }
    }

    @Test
    fun `can handle votes across multiple proposals`() {
        val resultSlot = slot<Collection<VeVoteProposalResults>>()

        every { veVoteProposalResultRepository.saveAll(capture(resultSlot)) } returns emptyList()

        every { veVoteResultService.processVeVoteResults(any()) } answers
            {
                listOf(
                    // First proposal
                    VeVoteProposalResults(
                        id = "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7-1",
                        blockId = LOGS_VEVOTE_RESULTS[0].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[0].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[0].meta.blockTimestamp,
                        proposalId =
                            "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7",
                        choice = 1,
                        totalWeight = BigDecimal("100"),
                        totalVoters = 1,
                    ),
                    // Second proposal
                    VeVoteProposalResults(
                        id = "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a-1",
                        blockId = LOGS_VEVOTE_RESULTS[9].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[9].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[9].meta.blockTimestamp,
                        proposalId =
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                        choice = 1,
                        totalWeight = BigDecimal("2440"),
                        totalVoters = 2,
                    ),
                    VeVoteProposalResults(
                        id = "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a-3",
                        blockId = LOGS_VEVOTE_RESULTS[9].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[9].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[9].meta.blockTimestamp,
                        proposalId =
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                        choice = 3,
                        totalWeight = BigDecimal("1170"),
                        totalVoters = 1,
                    ),
                    VeVoteProposalResults(
                        id = "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a-4",
                        blockId = LOGS_VEVOTE_RESULTS[9].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[9].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[9].meta.blockTimestamp,
                        proposalId =
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                        choice = 4,
                        totalWeight = BigDecimal("1170"),
                        totalVoters = 1,
                    ),
                )
            }

        // Process logs
        voteResultsIndexer.processLogs(LOGS_VEVOTE_RESULTS, emptyList())

        // Verify results
        val capturedResults = resultSlot.captured
        expect {
            that(capturedResults).hasSize(4)

            // Check we have results for both proposal IDs
            that(
                    capturedResults.count {
                        it.proposalId ==
                            "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7"
                    }
                )
                .isEqualTo(1)
            that(
                    capturedResults.count {
                        it.proposalId ==
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a"
                    }
                )
                .isEqualTo(3)

            // Check proposal 1 details
            val proposal1 =
                capturedResults.first {
                    it.proposalId ==
                        "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7"
                }
            that(proposal1.choice).isEqualTo(1)
            that(proposal1.totalWeight).isEqualTo(BigDecimal("100"))
            that(proposal1.totalVoters).isEqualTo(1)

            // Check total vote count for proposal 2
            val proposal2VoteCount =
                capturedResults
                    .filter {
                        it.proposalId ==
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a"
                    }
                    .sumOf { it.totalVoters }
            that(proposal2VoteCount).isEqualTo(4)

            // Check total weight for proposal 2
            val proposal2TotalWeight =
                capturedResults
                    .filter {
                        it.proposalId ==
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a"
                    }
                    .sumOf { it.totalWeight }
            that(proposal2TotalWeight).isEqualTo(BigDecimal("4780"))
        }
    }

    @Test
    fun `can handle split votes with weighted distribution`() {
        val resultSlot = slot<Collection<VeVoteProposalResults>>()

        every { veVoteProposalResultRepository.saveAll(capture(resultSlot)) } returns emptyList()

        every { veVoteResultService.processVeVoteResults(any()) } answers
            {
                listOf(
                    // A voter with 2340 weight voting for 3 choices (weight divided equally)
                    VeVoteProposalResults(
                        id = "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a-1",
                        blockId = LOGS_VEVOTE_RESULTS[9].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[9].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[9].meta.blockTimestamp,
                        proposalId =
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                        choice = 1,
                        totalWeight = BigDecimal("780.000000000000000000"),
                        totalVoters = 1,
                    ),
                    VeVoteProposalResults(
                        id = "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a-3",
                        blockId = LOGS_VEVOTE_RESULTS[9].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[9].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[9].meta.blockTimestamp,
                        proposalId =
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                        choice = 3,
                        totalWeight = BigDecimal("780.000000000000000000"),
                        totalVoters = 1,
                    ),
                    VeVoteProposalResults(
                        id = "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a-4",
                        blockId = LOGS_VEVOTE_RESULTS[9].meta.blockID,
                        blockNumber = LOGS_VEVOTE_RESULTS[9].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_RESULTS[9].meta.blockTimestamp,
                        proposalId =
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                        choice = 4,
                        totalWeight = BigDecimal("780.000000000000000000"),
                        totalVoters = 1,
                    ),
                )
            }

        // Process logs
        voteResultsIndexer.processLogs(LOGS_VEVOTE_RESULTS, emptyList())

        // Verify results
        val capturedResults = resultSlot.captured
        expect {
            that(capturedResults).hasSize(3)

            // Check all choices have the same weight (2340 ÷ 3 = 780)
            that(capturedResults.all { it.totalWeight == BigDecimal("780.000000000000000000") })
                .isEqualTo(true)

            // Check all choices have the same vote count
            that(capturedResults.all { it.totalVoters == 1 }).isEqualTo(true)

            // Check the sum of weights equals the original vote weight
            val totalWeight = capturedResults.sumOf { it.totalWeight }
            that(totalWeight).isEqualTo(BigDecimal("2340.000000000000000000"))

            // Check the correct choices were recorded
            that(capturedResults.map { it.choice }.sorted()).isEqualTo(listOf(1, 3, 4))
        }
    }
}
