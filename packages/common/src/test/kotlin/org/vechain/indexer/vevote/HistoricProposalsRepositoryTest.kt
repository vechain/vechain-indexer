package org.vechain.indexer.vevote

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoricProposalsRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: HistoricProposalsWriteRepository
    private lateinit var reader: HistoricProposalsReadRepository

    private val contract = "0x" + "cc".repeat(20)
    private val alice = "0x" + "aa".repeat(20)
    private val bob = "0x" + "bb".repeat(20)

    @BeforeAll
    fun start() {
        database.start()
        writer = HistoricProposalsWriteRepository(database.jdbc)
        reader = HistoricProposalsReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Proposal 1 has two votes; proposal 2 has none and keeps the tally the contract reported. */
    private fun seed() {
        writer.save(
            listOf(proposal("1", 10), proposal("2", 20, test = true)),
            listOf(HistoricProposalDescription(contract, "1", "ipfs://new", 40)),
            listOf(vote("1", alice, listOf(1), 30), vote("1", bob, listOf(1, 3), 30)),
        )
    }

    private fun description(id: String) =
        reader.find(id, null, null, 0, 10, DESC).single().description

    @Test
    fun `a proposal round-trips with the description published after it`() {
        val proposal = reader.find("1", null, null, 0, 10, DESC).single()

        assertEquals("$contract-1", proposal.id)
        assertEquals(listOf("yes", "no", "abstain"), proposal.choices)
        assertEquals("ipfs://new", proposal.description)
        assertEquals("title 1", proposal.title)
        assertEquals(alice, proposal.proposer)
        assertEquals("100", proposal.createdDate)
    }

    @Test
    fun `the tally counts the votes, an option nobody picked included`() {
        val proposal = reader.find("1", null, null, 0, 10, DESC).single()

        assertEquals(listOf(2L, 0L, 1L), proposal.voteTallies)
        assertEquals(3L, proposal.totalVotes)
    }

    @Test
    fun `a proposal with no votes keeps the tally the contract reported`() {
        val proposal = reader.find("2", null, null, 0, 10, DESC).single()

        assertEquals(listOf(5L, 6L, 7L), proposal.voteTallies)
        assertEquals(18L, proposal.totalVotes)
    }

    @Test
    fun `every filter narrows the page`() {
        assertEquals(2, reader.find(null, null, null, 0, 10, DESC).size)
        assertEquals(
            listOf(20L, 10L),
            reader.find(null, contract, null, 0, 10, DESC).map { it.blockNumber },
        )
        assertEquals("2", reader.find(null, null, true, 0, 10, DESC).single().proposalId)
        assertEquals("1", reader.find(null, null, false, 0, 10, DESC).single().proposalId)
        assertEquals(0, reader.find("not a number", null, null, 0, 10, DESC).size)
    }

    @Test
    fun `re-indexing a proposal replaces the options it was created with`() {
        writer.save(
            listOf(proposal("1", 10).copy(choices = listOf("yes"))),
            emptyList(),
            emptyList(),
        )

        val proposal = reader.find("1", null, null, 0, 10, DESC).single()
        assertEquals(listOf("yes"), proposal.choices)
        assertEquals(listOf(2L), proposal.voteTallies)

        writer.truncate()
        seed()
    }

    @Test
    fun `a description a rollback undoes stops being served`() {
        writer.save(
            emptyList(),
            listOf(HistoricProposalDescription(contract, "1", "ipfs://newer", 50)),
            emptyList(),
        )
        assertEquals("ipfs://newer", description("1"))

        writer.rollbackFrom(50)
        assertEquals("ipfs://new", description("1"))

        writer.rollbackFrom(40)
        assertEquals("", description("1"))

        writer.truncate()
        seed()
    }

    @Test
    fun `a description for a proposal that was never indexed is dropped`() {
        writer.save(
            emptyList(),
            listOf(HistoricProposalDescription(contract, "99", "ipfs://nowhere", 50)),
            emptyList(),
        )

        assertEquals(0, reader.find("99", null, null, 0, 10, DESC).size)
    }

    @Test
    fun `rollback drops the proposals and votes it undoes, options with them`() {
        writer.rollbackFrom(20)

        assertNull(reader.find("2", null, null, 0, 10, DESC).firstOrNull())
        assertEquals(3, database.count(HistoricProposalRowMapping.CHOICE_TABLE))
        // The votes came later, so they go too, and the on-chain tally serves again.
        assertEquals(
            listOf(1L, 2L, 3L),
            reader.find("1", null, null, 0, 10, DESC).single().voteTallies,
        )

        writer.truncate()
        seed()
    }

    private fun proposal(id: String, block: Long, test: Boolean = false) =
        HistoricProposals(
            id = "$contract-$id",
            proposalId = id,
            contractAddress = contract,
            createdDate = (block * 10).toString(),
            proposer = alice,
            title = "title $id",
            description = "",
            proposalType = 1,
            choices = listOf("yes", "no", "abstain"),
            test = test,
            createTime = 1,
            votingStartTime = 2,
            votingEndTime = 3,
            voteTallies = if (test) listOf(5L, 6L, 7L) else listOf(1L, 2L, 3L),
            totalVotes = if (test) 18L else 6L,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )

    private fun vote(proposalId: String, voter: String, choices: List<Int>, block: Long) =
        HistoricProposalsVote(
            proposalId = proposalId,
            contract = contract,
            voter = voter,
            choices = choices,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )
}
