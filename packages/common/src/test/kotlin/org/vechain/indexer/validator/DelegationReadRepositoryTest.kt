package org.vechain.indexer.validator

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.stargate.token.TokenLevel

/** One test per read on a seeded set; see [seed] for who delegated what to whom. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelegationReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: DelegationReadRepository

    private val alice = "0x" + "1".repeat(40)
    private val bob = "0x" + "2".repeat(40)
    private val carol = "0x" + "3".repeat(40)
    private val owner = "0x" + "a".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        repository = DelegationReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun delegation(
        id: String,
        block: Long,
        validator: String,
        status: DelegationStatus,
        level: TokenLevel = TokenLevel.Dawn,
        transitionAt: Long? = null,
        tokenId: String = id,
    ) =
        Delegation(
            id = id,
            validator = validator,
            tokenId = tokenId,
            owner = owner,
            status = status,
            tokenLevel = level,
            stakedAmount = level.staked.toBigInteger().multiply(BigInteger.TEN.pow(18)).toString(),
            totalRewardsClaimed = BigInteger.ZERO,
            txId = "0x" + block.toString(16).padStart(64, '0'),
            transitionAtBlock = transitionAt,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )

    /**
     * alice: 1 ACTIVE Dawn, 2 QUEUED Dawn due at 500, 3 EXITING Flash due at 600, 4 EXITED; bob: 5
     * ACTIVE Flash; carol: 6 ACTIVE at 10, EXITED at 20. Delegation 1 was re-stated at block 20.
     */
    private fun seed() {
        val writer = DelegationWriteRepository(database.jdbc)
        writer.save(
            listOf(
                delegation("1", 10, alice, DelegationStatus.QUEUED, transitionAt = 20),
                delegation("2", 10, alice, DelegationStatus.QUEUED, transitionAt = 500),
                delegation("3", 10, alice, DelegationStatus.EXITING, TokenLevel.Flash, 600),
                delegation("4", 10, alice, DelegationStatus.EXITED),
                delegation("5", 10, bob, DelegationStatus.ACTIVE, TokenLevel.Flash),
                delegation("6", 10, carol, DelegationStatus.ACTIVE),
            )
        )
        writer.save(
            listOf(
                delegation("1", 20, alice, DelegationStatus.ACTIVE),
                delegation("6", 20, carol, DelegationStatus.EXITED),
            )
        )
    }

    private fun ids(rows: List<Delegation>) = rows.map { it.id }

    @Test
    fun `the unfiltered page is newest block first with the id as tiebreak`() {
        assertEquals(
            listOf("6", "1", "5"),
            ids(repository.find(null, null, null, Direction.DESC, 0, 3)),
        )
        assertEquals(
            listOf("4", "5"),
            ids(repository.find(null, null, null, Direction.ASC, 2, 2)),
        )
    }

    @Test
    fun `validator, token and status filters combine`() {
        val active = listOf(DelegationStatus.ACTIVE, DelegationStatus.EXITING)
        assertEquals(
            listOf("3", "1"),
            ids(repository.find(alice, null, active, Direction.ASC, 0, 10)),
        )
        assertEquals(listOf("5"), ids(repository.find(null, "5", null, Direction.ASC, 0, 10)))
        assertEquals(
            emptyList<String>(),
            ids(repository.find(bob, "5", listOf(DelegationStatus.QUEUED), Direction.ASC, 0, 10)),
        )
    }

    @Test
    fun `activeAsOf is the token-reward read, as the delegations stood at the block`() {
        assertEquals(listOf("3"), ids(repository.activeAsOf(alice, 19)))
        assertEquals(listOf("1", "3"), ids(repository.activeAsOf(alice, 20)))
        assertEquals(emptyList<String>(), ids(repository.activeAsOf(alice, 9)))
        // The row superseded at 20 stands at 19 and not at 20.
        assertEquals(listOf("6"), ids(repository.activeAsOf(carol, 19)))
        assertEquals(emptyList<String>(), ids(repository.activeAsOf(carol, 20)))
    }

    @Test
    fun `counts skip exited delegations`() {
        assertEquals(
            listOf(DelegationStatusCounts(alice, 1, 1, 1), DelegationStatusCounts(bob, 0, 1, 0)),
            repository.countsByValidator(),
        )
        assertEquals(
            listOf(DelegationStatusCounts(bob, 0, 1, 0)),
            repository.countsByValidator(bob),
        )
    }

    @Test
    fun `facets bucket a validator's live delegations by status, level and transition`() {
        assertEquals(
            listOf(
                DelegationLevelFacet(alice, "QUEUED", "Dawn", 500, 1),
                DelegationLevelFacet(alice, "ACTIVE", "Dawn", null, 1),
                DelegationLevelFacet(alice, "EXITING", "Flash", 600, 1),
                DelegationLevelFacet(bob, "ACTIVE", "Flash", null, 1),
            ),
            repository.aggregateDelegationFacetsByValidators(listOf(alice, bob)),
        )
    }
}
