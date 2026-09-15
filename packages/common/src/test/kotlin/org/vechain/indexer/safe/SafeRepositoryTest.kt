package org.vechain.indexer.safe

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SafeRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: SafeWriteRepository
    private lateinit var reader: SafeReadRepository

    private val safeA = "0x" + "11".repeat(20)
    private val safeB = "0x" + "22".repeat(20)
    private val ownerA = "0x" + "aa".repeat(20)
    private val ownerB = "0x" + "bb".repeat(20)
    private val txHash = "0x" + "dd".repeat(32)
    private val chainTx = "0x" + "cc".repeat(32)

    @BeforeAll
    fun start() {
        database.start()
        writer = SafeWriteRepository(database.jdbc)
        reader = SafeReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Two Safes; ownerA leaves the first at block 30, and one transaction is approved and run. */
    private fun seed() {
        writer.save(
            SafeUpdate(
                proxies = listOf(proxy(safeA, 10), proxy(safeB, 12)),
                memberships =
                    listOf(
                        membership(safeA, ownerA, 10),
                        membership(safeA, ownerB, 10),
                        membership(safeB, ownerB, 12),
                        membership(safeA, ownerA, 30, removedAt = 30),
                    ),
                txStates =
                    listOf(
                        txState(20, approvers = listOf(approval(ownerA, 20))),
                        txState(
                            25,
                            approvers = listOf(approval(ownerA, 20), approval(ownerB, 25)),
                            executed = true,
                        ),
                    ),
                proposals = listOf(proposal(18), proposal(22, withSubcalls = true)),
            )
        )
    }

    private fun blockId(block: Long) = "0x" + block.toString(16).padStart(64, '0')

    private fun proxy(address: String, block: Long) =
        SafeProxy(
            address = address,
            singleton = "0x" + "99".repeat(20),
            createdBlock = block,
            createdTimestamp = block * 100,
            vechainTxId = chainTx,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
        )

    private fun membership(safe: String, owner: String, block: Long, removedAt: Long? = null) =
        SafeMembership(
            id = SafeMembership.buildId(safe, owner),
            safe = safe,
            owner = owner,
            addedBlock = if (removedAt == null) block else 10,
            addedTimestamp = if (removedAt == null) block * 100 else 1000,
            removedBlock = removedAt,
            removedTimestamp = removedAt?.times(100),
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
        )

    private fun approval(owner: String, block: Long) =
        SafeTxApproval(owner, block, block * 100, chainTx)

    private fun txState(
        block: Long,
        approvers: List<SafeTxApproval>,
        executed: Boolean = false,
    ) =
        SafeTxState(
            id = SafeTxState.buildId(safeA, txHash),
            safe = safeA,
            txHash = txHash,
            approvers = approvers,
            executed = executed,
            failed = false,
            executor = if (executed) ownerB else null,
            executedBlock = if (executed) block else null,
            executedTimestamp = if (executed) block * 100 else null,
            vechainTxId = if (executed) chainTx else null,
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
        )

    private fun proposal(block: Long, withSubcalls: Boolean = false) =
        SafeTxProposal(
            id = SafeTxProposal.buildId(safeA, txHash),
            safe = safeA,
            txHash = txHash,
            proposer = ownerA,
            proposedBlock = block,
            proposedTimestamp = block * 100,
            proposedVechainTxId = chainTx,
            to = ownerB,
            value = BigInteger("1000000000000000000"),
            data = "0xdeadbeef",
            operation = 0,
            nonce = BigInteger("3"),
            description = "pay the bill",
            safeTxGas = BigInteger("21000"),
            subcalls =
                if (!withSubcalls) null
                else
                    listOf(
                        SafeSubcall(ownerB, BigInteger.ONE, "0x", 0, "first"),
                        SafeSubcall(ownerA, BigInteger.TWO, "0x01", 1, "second"),
                    ),
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
        )

    @Test
    fun `an owner's memberships read back current, past or both`() {
        assertEquals(
            listOf(safeB, safeA),
            reader
                .findMembershipsByOwner(ownerB, SafeMembershipScope.ALL, 0, 10, Direction.DESC)
                .map { it.safe },
        )
        assertEquals(
            emptyList<String>(),
            reader
                .findMembershipsByOwner(ownerA, SafeMembershipScope.CURRENT, 0, 10, Direction.DESC)
                .map { it.safe },
        )
        val past =
            reader
                .findMembershipsByOwner(ownerA, SafeMembershipScope.PAST, 0, 10, Direction.DESC)
                .single()
        assertEquals(30L, past.removedBlock)
        assertEquals(10L, past.addedBlock)
        assertEquals(SafeMembership.buildId(safeA, ownerA), past.id)
    }

    @Test
    fun `a transaction's state reads back with its approvals in one query`() {
        val state = reader.findTxState(safeA, txHash)!!

        assertTrue(state.executed)
        assertEquals(ownerB, state.executor)
        assertEquals(listOf(ownerA, ownerB), state.approvers.map { it.owner })
        assertEquals(listOf(20L, 25L), state.approvers.map { it.block })
        assertNull(reader.findTxState(safeB, txHash))
    }

    @Test
    fun `a proposal reads back with its subcalls in order`() {
        val proposal = reader.findProposalsBySafe(safeA, 0, 10, Direction.DESC).single()

        assertEquals(22L, proposal.blockNumber)
        assertEquals(BigInteger("1000000000000000000"), proposal.value)
        assertEquals("0xdeadbeef", proposal.data)
        assertEquals(BigInteger("21000"), proposal.safeTxGas)
        assertEquals(listOf("first", "second"), proposal.subcalls?.map { it.label })
        assertEquals(listOf(ownerB, ownerA), proposal.subcalls?.map { it.target })
        assertEquals(
            emptyList<SafeTxProposal>(),
            reader.findProposalsBySafe(safeB, 0, 10, Direction.DESC),
        )
    }

    @Test
    fun `the indexer reads the current row of every key it touches`() {
        assertEquals(
            listOf(30L, 10L, 12L),
            writer
                .findCurrentMemberships(setOf(safeA to ownerA, safeA to ownerB, safeB to ownerB))
                .sortedWith(compareBy({ it.safe }, { it.owner }))
                .map { it.blockNumber },
        )
        assertEquals(
            listOf(ownerA, ownerB),
            writer.findCurrentTxStates(setOf(safeA to txHash)).single().approvers.map { it.owner },
        )
        assertEquals(
            2,
            writer.findCurrentProposals(setOf(safeA to txHash)).single().subcalls?.size,
        )
        assertEquals(setOf(safeA, safeB), writer.knownSafes(setOf(safeA, safeB, ownerA)))
        assertEquals(emptySet<String>(), writer.knownSafes(emptySet()))
    }

    @Test
    fun `rollback reopens the superseded rows and a deleted Safe takes its rows with it`() {
        writer.rollbackFrom(22)
        assertEquals(
            1,
            reader
                .findMembershipsByOwner(ownerA, SafeMembershipScope.CURRENT, 0, 10, Direction.DESC)
                .size,
        )
        assertEquals(listOf(ownerA), reader.findTxState(safeA, txHash)?.approvers?.map { it.owner })
        val reopened = reader.findProposalsBySafe(safeA, 0, 10, Direction.DESC).single()
        assertEquals(18L, reopened.blockNumber)
        assertNull(reopened.subcalls)

        // Rolling back past the deployment removes the Safe and everything hanging off it.
        writer.rollbackFrom(10)
        assertEquals(
            0,
            reader
                .findMembershipsByOwner(ownerB, SafeMembershipScope.ALL, 0, 10, Direction.DESC)
                .size,
        )
        assertEquals(0L, reader.latestBlockNumber())

        writer.truncate()
        seed()
    }

    @Test
    fun `a subcall carried onto a later row keeps the block it arrived in`() {
        writer.save(SafeUpdate(proposals = listOf(proposal(40, withSubcalls = true))))

        writer.rollbackFrom(40)
        val reopened = reader.findProposalsBySafe(safeA, 0, 10, Direction.DESC).single()
        assertEquals(22L, reopened.blockNumber)
        assertEquals(listOf("first", "second"), reopened.subcalls?.map { it.label })

        writer.truncate()
        seed()
    }

    @Test
    fun `the head is the newest block in the schema, not the newest deployment`() {
        assertEquals(30L, reader.latestBlockNumber())
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(10))
        assertEquals(2, writer.prune(26))
        assertEquals(
            30L,
            writer.findCurrentMemberships(setOf(safeA to ownerA)).single().blockNumber,
        )
        writer.truncate()
        seed()
    }
}
