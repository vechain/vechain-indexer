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
import org.vechain.indexer.service.VeVoteCommentService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.FileUtils
import org.vechain.indexer.vevote.VeVoteCommentIndexer
import strikt.api.expect
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
class VeVoteCommentIndexerTest {
    @MockK lateinit var vevoteCommentRepository: VevoteCommentRepository

    @MockK lateinit var veVoteCommentService: VeVoteCommentService

    @MockK lateinit var mongoTemplate: MongoTemplate

    private lateinit var vevoteCommentIndexer: VeVoteCommentIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        val abiFileStreams = FileUtils.loadFileStreams("test-abis")
        val abiManager = AbiManager()
        abiManager.loadAbis(abiFileStreams)

        vevoteCommentIndexer =
            VeVoteCommentIndexer(
                vevoteCommentRepository,
                veVoteCommentService,
                mongoTemplate,
                DefaultThorClient("http://localhost:8669"),
                abiManager,
                0L,
                1000L,
                1000L,
                contractAddress = "0x428be069e21a584fbbab934fb4ad55af346a3513",
            )
    }

    @Test
    fun `process block with no comment events`() {
        vevoteCommentIndexer.processBlock(BLOCK_NO_CLAUSES)

        verify { mongoTemplate wasNot Called }
    }

    @Test
    fun `can handle different vote choices`() {
        // Set up to capture what gets inserted into MongoDB
        val commentsSlot = slot<Collection<VevoteProposalComment>>()
        every {
            mongoTemplate.insert(capture(commentsSlot), VevoteProposalComment::class.java)
        } returns mutableListOf()

        every { veVoteCommentService.processComment(any()) } answers
            {
                listOf(
                    VevoteProposalComment(
                        id = "id1",
                        blockId = LOGS_VEVOTE_COMMENTS[0].meta.blockID,
                        blockNumber = LOGS_VEVOTE_COMMENTS[0].meta.blockNumber,
                        blockTimestamp = LOGS_VEVOTE_COMMENTS[0].meta.blockTimestamp,
                        voter = "0x96b1f3434b7bdec11955db84fe55e60f67b4651f",
                        proposalId =
                            "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7",
                        choices = listOf(1),
                        weight = BigInteger.valueOf(100),
                        reason = "",
                    ),
                    VevoteProposalComment(
                        id = "id2",
                        blockId = LOGS_VEVOTE_COMMENTS[9].meta.blockID,
                        blockNumber = LOGS_VEVOTE_COMMENTS[9].meta.blockTimestamp,
                        blockTimestamp = LOGS_VEVOTE_COMMENTS[9].meta.blockTimestamp,
                        voter = "0x08c7bfbacb25c6aabf86ddfa42dba9a570da0884",
                        proposalId =
                            "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                        choices = listOf(1, 3, 4),
                        weight = BigInteger.valueOf(2340),
                        reason = "I am voting for choice A, Choice C and Choice D",
                    ),
                )
            }

        // Process logs
        vevoteCommentIndexer.processLogs(LOGS_VEVOTE_COMMENTS, emptyList())

        // Verify the captured comments
        val comments = commentsSlot.captured
        expect {
            that(comments).hasSize(2)
            that(comments.any { it.choices.size == 1 }).isEqualTo(true)
            that(comments.any { it.choices.size > 1 }).isEqualTo(true)
            that(comments.any { it.choices.containsAll(listOf(1, 3, 4)) }).isEqualTo(true)
        }
    }

    @Test
    fun `can process votes from the same user on different proposals`() {
        val commentsSlot = slot<Collection<VevoteProposalComment>>()
        every {
            mongoTemplate.insert(capture(commentsSlot), VevoteProposalComment::class.java)
        } returns mutableListOf()

        every { veVoteCommentService.processComment(any()) } returns
            listOf(
                VevoteProposalComment(
                    id = "id1",
                    blockId = LOGS_VEVOTE_COMMENTS[0].meta.blockID,
                    blockNumber = LOGS_VEVOTE_COMMENTS[0].meta.blockNumber,
                    blockTimestamp = LOGS_VEVOTE_COMMENTS[0].meta.blockTimestamp,
                    voter = "0x96b1f3434b7bdec11955db84fe55e60f67b4651f",
                    proposalId =
                        "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7",
                    choices = listOf(1),
                    weight = BigInteger.valueOf(100),
                    reason = "",
                ),
                VevoteProposalComment(
                    id = "id2",
                    blockId = LOGS_VEVOTE_COMMENTS[9].meta.blockID,
                    blockNumber = LOGS_VEVOTE_COMMENTS[9].meta.blockNumber,
                    blockTimestamp = LOGS_VEVOTE_COMMENTS[9].meta.blockTimestamp,
                    voter = "0x96b1f3434b7bdec11955db84fe55e60f67b4651f",
                    proposalId =
                        "0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a",
                    choices = listOf(1),
                    weight = BigInteger.valueOf(100),
                    reason = "",
                ),
            )

        vevoteCommentIndexer.processLogs(LOGS_VEVOTE_COMMENTS, emptyList())

        // Verify the captured comments
        val comments = commentsSlot.captured
        expect {
            that(comments).hasSize(2)
            that(comments.count { it.voter == "0x96b1f3434b7bdec11955db84fe55e60f67b4651f" })
                .isEqualTo(2)
            that(comments.map { it.proposalId })
                .contains("0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7")
            that(comments.map { it.proposalId })
                .contains("0x18bbf9d6523cd56284611970cfc201a1e6a9628788f0f32a91da3202996be58a")
        }
    }

    @Test
    fun `can process votes with long comments`() {
        // Set up to capture what gets inserted into MongoDB
        val commentsSlot = slot<Collection<VevoteProposalComment>>()
        every {
            mongoTemplate.insert(capture(commentsSlot), VevoteProposalComment::class.java)
        } returns mutableListOf()

        val longComment =
            "While I see valid arguments presented by both sides of this proposal, I currently lack sufficient clarity on the long-term implications of the proposed changes. There are nuanced trade-offs that I feel require more discussion or deeper community engagement before I can confidently take a firm stance. Out of respect for the process and to avoid unintentionally skewing the outcome without being fully informed, I am choosing to abstain from this vote."

        every { veVoteCommentService.processComment(any()) } returns
            listOf(
                VevoteProposalComment(
                    id = "id1",
                    blockId = LOGS_VEVOTE_COMMENTS[7].meta.blockID,
                    blockNumber = LOGS_VEVOTE_COMMENTS[7].meta.blockNumber,
                    blockTimestamp = LOGS_VEVOTE_COMMENTS[7].meta.blockTimestamp,
                    voter = "0x08c7bfbacb25c6aabf86ddfa42dba9a570da0884",
                    proposalId =
                        "0x88e775074909d05136236baa138887aa44ba5881d740650e6f45a50a0c0fe2d7",
                    choices = listOf(4),
                    weight = BigInteger.valueOf(2340),
                    reason = longComment,
                ),
            )

        vevoteCommentIndexer.processLogs(LOGS_VEVOTE_COMMENTS, emptyList())

        val comments = commentsSlot.captured
        expect {
            that(comments).hasSize(1)
            that(comments.first().reason).isEqualTo(longComment)
            that(comments.first().reason.length > 100).isEqualTo(true)
        }
    }
}
