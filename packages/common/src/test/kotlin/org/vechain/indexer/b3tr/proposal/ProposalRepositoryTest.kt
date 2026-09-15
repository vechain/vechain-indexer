package org.vechain.indexer.b3tr.proposal

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ProposalWriteRepository
    private lateinit var results: ProposalResultReadRepository
    private lateinit var comments: ProposalCommentReadRepository

    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)

    @BeforeAll
    fun start() {
        database.start()
        writer = ProposalWriteRepository(database.jdbc)
        results = ProposalResultReadRepository(database.jdbc)
        comments = ProposalCommentReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Proposal 1 is created, voted on, then executed; proposal 2 stays open with no votes. */
    private fun seed() {
        writer.save(
            listOf(
                result("1", 10),
                result("2", 15),
                result("1", 20, tally = 3),
                result("1", 30, state = ProposalState.Executed, tally = 3),
            ),
            listOf(comment("1", alice, 20, "well argued"), comment("1", bob, 20, "no thanks")),
        )
    }

    private fun result(
        proposalId: String,
        block: Long,
        state: ProposalState = ProposalState.Pending,
        tally: Long? = null,
    ) =
        ProposalResult(
            proposalId = proposalId,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            createdAtBlockNumber = if (proposalId == "1") 10 else 15,
            startRoundId = 7,
            state = state,
            results =
                tally?.let {
                    VoteResults(
                        forResult = Result(it, BigInteger.valueOf(it * 100), BigInteger.TEN),
                        againstResult = Result(0, BigInteger.ZERO, BigInteger.ZERO),
                        abstainResult = Result(1, BigInteger.ONE, BigInteger.ONE),
                    )
                },
            description = "proposal $proposalId",
        )

    private fun comment(proposalId: String, voter: String, block: Long, reason: String) =
        ProposalComment(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            voter = voter,
            proposalId = proposalId,
            support = if (voter == alice) Support.FOR else Support.AGAINST,
            weight = BigInteger.valueOf(100),
            power = BigInteger.TEN,
            reason = reason,
        )

    @Test
    fun `a result round-trips and only its newest row is current`() {
        val current = results.findByProposalId("1")!!
        assertEquals(30L, current.blockNumber)
        assertEquals(10L, current.createdAtBlockNumber)
        assertEquals(ProposalState.Executed, current.state)
        assertEquals(3L, current.results?.forResult?.voters)
        assertEquals(BigInteger.valueOf(300), current.results?.forResult?.totalWeight)
        assertNull(results.findByProposalId("2")?.results)
        assertNull(results.findByProposalId("not a number"))
    }

    @Test
    fun `results page newest first and filter on the states asked for`() {
        assertEquals(
            listOf("2", "1"),
            results.find(emptyList(), 0, 10, Direction.DESC).map { it.proposalId },
        )
        assertEquals(
            listOf("1"),
            results.find(listOf(ProposalState.Executed), 0, 10, Direction.DESC).map {
                it.proposalId
            },
        )
        assertEquals(1, results.find(emptyList(), 1, 10, Direction.ASC).size)
    }

    @Test
    fun `comments filter by proposal, voter and support`() {
        assertEquals(2, comments.find("1", null, null, 0, 10, Direction.DESC).size)
        assertEquals(
            listOf("well argued"),
            comments.find(null, alice, null, 0, 10, Direction.DESC).map { it.reason },
        )
        assertEquals(1, comments.find("1", null, Support.AGAINST, 0, 10, Direction.DESC).size)
        assertEquals(0, comments.find("2", null, null, 0, 10, Direction.DESC).size)
    }

    @Test
    fun `a voter's second comment replaces the first`() {
        writer.save(emptyList(), listOf(comment("1", alice, 40, "changed my mind")))

        val reasons = comments.find("1", alice, null, 0, 10, Direction.DESC)
        assertEquals(listOf("changed my mind"), reasons.map { it.reason })
        assertEquals(40L, reasons[0].blockNumber)

        writer.truncate()
        seed()
    }

    @Test
    fun `rollback reopens the superseded row and truncate empties the schema`() {
        writer.rollbackFrom(20)
        assertEquals(10L, results.findByProposalId("1")?.blockNumber)
        assertEquals(0, comments.find("1", null, null, 0, 10, Direction.DESC).size)

        writer.truncate()
        assertEquals(0L, results.latestBlockNumber())
        assertEquals(0L, comments.latestBlockNumber())
        seed()
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(20))
        assertEquals(1, writer.prune(21))
        assertEquals(30L, results.findByProposalId("1")?.blockNumber)
        writer.truncate()
        seed()
    }

    @Test
    fun `the indexer reads the current row of every proposal it touches`() {
        assertEquals(
            listOf("1" to 30L, "2" to 15L),
            writer
                .findCurrent(setOf("1", "2"))
                .map { it.proposalId to it.blockNumber }
                .sortedBy { it.first },
        )
        assertEquals(
            listOf("2"),
            writer.findCurrentByStates(ProposalState.nonFinalizedStates).map { it.proposalId },
        )
        assertEquals(emptyList<ProposalResult>(), writer.findCurrent(emptySet()))
    }
}
