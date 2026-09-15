package org.vechain.indexer.vevote

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VeVoteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: VeVoteWriteRepository
    private lateinit var comments: VeVoteCommentReadRepository
    private lateinit var results: VeVoteResultReadRepository

    private val proposal = "1".repeat(65)
    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)

    @BeforeAll
    fun start() {
        database.start()
        writer = VeVoteWriteRepository(database.jdbc)
        comments = VeVoteCommentReadRepository(database.jdbc)
        results = VeVoteResultReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Alice votes FOR at block 10, Bob AGAINST at block 20, each with a comment. */
    private fun seed() {
        writer.save(
            listOf(comment(1, alice, Support.FOR, 10)),
            listOf(result(Support.FOR, 10, "10", 1)),
        )
        writer.save(
            listOf(comment(2, bob, Support.AGAINST, 20)),
            listOf(result(Support.FOR, 20, "25", 2)),
        )
    }

    @Test
    fun `a comment round-trips and every filter narrows the page`() {
        val page = comments.find(null, null, null, 0, 10, DESC)

        assertEquals(listOf(20L, 10L), page.map { it.blockNumber })
        assertEquals(comment(2, bob, Support.AGAINST, 20), page.first())
        assertEquals(1, comments.find(null, alice, null, 0, 10, DESC).size)
        assertEquals(1, comments.find(proposal, null, Support.FOR, 0, 10, DESC).size)
        assertEquals(0, comments.find(proposal, alice, Support.AGAINST, 0, 10, DESC).size)
        assertEquals(
            listOf(10L),
            comments.find(null, null, null, 1, 10, DESC).map { it.blockNumber },
        )
    }

    @Test
    fun `a reason carrying NUL survives the round trip`() {
        val withNul = comment(3, alice, Support.FOR, 30).copy(reason = "keep\u0000 it")
        writer.save(listOf(withNul), emptyList())

        assertEquals(withNul, comments.find(null, null, null, 0, 1, DESC).single())

        writer.truncate()
        seed()
    }

    @Test
    fun `the same reason is written once, whichever entry it repeats in`() {
        writer.save(listOf(comment(1, bob, Support.ABSTAIN, 30)), emptyList())
        // A repeat inside one entry keeps the same row a repeat in a later entry would.
        writer.save(
            listOf(comment(4, alice, Support.FOR, 40), comment(4, bob, Support.AGAINST, 50)),
            emptyList(),
        )

        assertEquals(
            comment(1, alice, Support.FOR, 10),
            comments.find(null, alice, null, 0, 10, DESC).last(),
        )
        assertEquals(
            comment(4, alice, Support.FOR, 40),
            comments.find(null, null, null, 0, 1, DESC).single(),
        )
        assertEquals(3, database.count(VeVoteCommentRowMapping.TABLE))

        writer.truncate()
        seed()
    }

    @Test
    fun `only a result's newest row is current`() {
        assertEquals(
            listOf(20L),
            results.find(proposal, Support.FOR, 0, 10, DESC).map { it.blockNumber },
        )
        assertEquals(
            result(Support.FOR, 20, "25", 2),
            results.find(null, Support.FOR, 0, 10, DESC).single(),
        )
    }

    @Test
    fun `rollback undoes both tables together and reopens what the block superseded`() {
        writer.rollbackFrom(20)

        assertEquals(
            result(Support.FOR, 10, "10", 1),
            results.find(proposal, null, 0, 10, DESC).single(),
        )
        assertEquals(
            listOf(10L),
            comments.find(null, null, null, 0, 10, DESC).map { it.blockNumber },
        )

        writer.truncate()
        seed()
    }

    @Test
    fun `prune drops superseded results below the horizon and reports how many`() {
        assertEquals(0, writer.prune(20))
        assertEquals(1, writer.prune(21))
        assertEquals(20L, results.find(proposal, null, 0, 10, DESC).single().blockNumber)

        writer.truncate()
        seed()
    }

    private fun blockId(block: Long) = "0x" + block.toString(16).padStart(64, '0')

    private fun comment(n: Int, voter: String, support: Support, block: Long) =
        VeVoteProposalComment(
            id = "%040d".format(n),
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 10,
            voter = voter,
            proposalId = proposal,
            support = support,
            weight = BigInteger.valueOf(block),
            reason = "reason $n",
        )

    private fun result(support: Support, block: Long, weight: String, voters: Int) =
        VeVoteProposalResult(
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 10,
            proposalId = proposal,
            support = support,
            totalWeight = BigDecimal(weight),
            totalVoters = voters,
        )
}
