package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_NO_CLAUSES
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_VEVOTE_COMMENTS
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.service.CommentService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.FileUtils

@ExtendWith(MockKExtension::class)
class VeVoteCommentIndexerTest {
    @MockK lateinit var vevoteCommentRepository: VevoteCommentRepository

    @MockK lateinit var commentService: CommentService

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var vevoteCommentIndexer: VevoteCommentIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val abiFileStreams = FileUtils.loadFileStreams("test-abis")
        val abiManager = AbiManager()
        abiManager.loadAbis(abiFileStreams)

        vevoteCommentIndexer =
            VevoteCommentIndexer(
                vevoteCommentRepository,
                commentService,
                mongoTemplate,
                DefaultThorClient("http://localhost:8669"),
                abiManager,
                0L,
                1000L,
                1000L,
                contractAddress = "0x",
            )
    }

    @Test
    fun `process block with no comment events`() {
        vevoteCommentIndexer.processBlock(BLOCK_NO_CLAUSES)

        verify { mongoTemplate wasNot Called }
    }

    @Test
    fun `can handle different vote choices`() {
        // Create mock comments for different vote choices
        val singleChoiceVote =
            mockk<VevoteProposalComment> {
                every { voter } returns "0x96b1f3434b7bdec11955db84fe55e60f67b4651f"
                every { proposalId } returns
                    "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7"
                every { choices } returns listOf(1)
                every { weight } returns BigInteger.valueOf(100)
                every { reason } returns ""
                every { blockNumber } returns 21432834L
            }

        val multiChoiceVote =
            mockk<VevoteProposalComment> {
                every { voter } returns "0x08c7bfbacb25c6aabf86ddfa42dba9a570da0884"
                every { proposalId } returns
                    "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a"
                every { choices } returns listOf(1, 3, 4)
                every { weight } returns BigInteger.valueOf(2340)
                every { reason } returns "I am voting for choice A, Choice C and Choice D"
                every { blockNumber } returns 21432977L
            }

        // Make commentService.processComment return our mock comments
        every { commentService.processComment(any()) } returns
            listOf(singleChoiceVote, multiChoiceVote)
        every {
            mongoTemplate.insert(
                any<Collection<VevoteProposalComment>>(),
                VevoteProposalComment::class.java
            )
        } returns mutableListOf()

        // Process logs
        vevoteCommentIndexer.processLogs(LOGS_VEVOTE_COMMENTS, emptyList())

        // Verify both types of votes were processed
        verify {
            mongoTemplate.insert(
                match<Collection<VevoteProposalComment>> {
                    it.any { vote -> vote.choices.size == 1 } &&
                        it.any { vote -> vote.choices.containsAll(listOf(1, 3, 4)) }
                },
                VevoteProposalComment::class.java
            )
        }
    }

    @Test
    fun `can process votes from the same user on different proposals`() {
        // Create mock comments for the same user voting on different proposals
        val firstProposalVote =
            mockk<VevoteProposalComment> {
                every { voter } returns "0x96b1f3434b7bdec11955db84fe55e60f67b4651f"
                every { proposalId } returns
                    "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7"
                every { choices } returns listOf(1)
                every { weight } returns BigInteger.valueOf(100)
                every { reason } returns ""
                every { blockNumber } returns 21432834L
            }

        val secondProposalVote =
            mockk<VevoteProposalComment> {
                every { voter } returns "0x96b1f3434b7bdec11955db84fe55e60f67b4651f"
                every { proposalId } returns
                    "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a"
                every { choices } returns listOf(1)
                every { weight } returns BigInteger.valueOf(100)
                every { reason } returns ""
                every { blockNumber } returns 21432958L
            }

        // Make commentService.processComment return our mock comments
        every { commentService.processComment(any()) } returns
            listOf(firstProposalVote, secondProposalVote)
        every {
            mongoTemplate.insert(
                any<Collection<VevoteProposalComment>>(),
                VevoteProposalComment::class.java
            )
        } returns mutableListOf()

        vevoteCommentIndexer.processLogs(LOGS_VEVOTE_COMMENTS, emptyList())

        verify {
            mongoTemplate.insert(
                match<Collection<VevoteProposalComment>> {
                    it.count { vote ->
                        vote.voter == "0x96b1f3434b7bdec11955db84fe55e60f67b4651f"
                    } == 2 &&
                        it.any { vote ->
                            vote.proposalId ==
                                "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7"
                        } &&
                        it.any { vote ->
                            vote.proposalId ==
                                "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a"
                        }
                },
                VevoteProposalComment::class.java
            )
        }
    }

    @Test
    fun `can process votes with long comments`() {
        // Create mock for a vote with a long comment
        val longCommentVote =
            mockk<VevoteProposalComment> {
                every { voter } returns "0x08c7bfbacb25c6aabf86ddfa42dba9a570da0884"
                every { proposalId } returns
                    "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7"
                every { choices } returns listOf(4)
                every { weight } returns BigInteger.valueOf(2340)
                every { reason } returns
                    "While I see valid arguments presented by both sides of this proposal, I currently lack sufficient clarity on the long-term implications of the proposed changes. There are nuanced trade-offs that I feel require more discussion or deeper community engagement before I can confidently take a firm stance. Out of respect for the process and to avoid unintentionally skewing the outcome without being fully informed, I am choosing to abstain from this vote."
                every { blockNumber } returns 21432877L
            }

        every { commentService.processComment(any()) } returns listOf(longCommentVote)
        every {
            mongoTemplate.insert(
                any<Collection<VevoteProposalComment>>(),
                VevoteProposalComment::class.java
            )
        } returns mutableListOf()

        vevoteCommentIndexer.processLogs(LOGS_VEVOTE_COMMENTS, emptyList())

        verify {
            mongoTemplate.insert(
                match<Collection<VevoteProposalComment>> {
                    it.any { vote -> vote.reason.length > 100 }
                },
                VevoteProposalComment::class.java
            )
        }
    }
}
