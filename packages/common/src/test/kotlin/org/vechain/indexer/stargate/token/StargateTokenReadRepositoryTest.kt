package org.vechain.indexer.stargate.token

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.DelegationWriteRepository
import org.vechain.indexer.validator.Status

/** One test per API read on a seeded set; see [seed] for who holds, manages and delegates what. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StargateTokenReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: StargateTokenReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val carol = "0x" + "c".repeat(40)
    private val v1 = "0x" + "1".repeat(40)
    private val v2 = "0x" + "2".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        repository = StargateTokenReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun token(tokenId: String, block: Long, owner: String, manager: String? = null) =
        StargateToken(
            tokenId = tokenId,
            level = TokenLevel.Dawn,
            owner = owner,
            manager = manager,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger.TEN,
            migrated = false,
            boosted = false,
            blockNumber = block,
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockTimestamp = block * 10,
        )

    /**
     * alice owns 1 (block 10) and 2 (block 30, managed by bob); bob owns 3 (block 20, managed by
     * carol); token 4 was alice's and is burned; token 1 was re-stated at block 40.
     */
    private fun seed() {
        val writer = StargateTokenWriteRepository(database.jdbc)
        writer.save(
            listOf(
                token("1", 10, alice),
                token("4", 10, alice),
                token("3", 20, bob, manager = carol),
                token("2", 30, alice, manager = bob),
            )
        )
        writer.save(listOf(token("1", 40, alice), token("4", 40, Address.ZERO_ADDRESS)))
        // 1 is live on v2 via 8, its older 5 exited at a later block; 2 exited; 3 queued on v1.
        val delegations = DelegationWriteRepository(database.jdbc)
        delegations.save(
            listOf(
                delegation("5", "1", v1, DelegationStatus.ACTIVE, 50),
                delegation("6", "2", v1, DelegationStatus.EXITED, 50),
                delegation("7", "3", v1, DelegationStatus.QUEUED, 50),
            )
        )
        delegations.save(
            listOf(
                delegation("8", "1", v2, DelegationStatus.ACTIVE, 60),
                delegation("7", "3", v1, DelegationStatus.QUEUED, 60),
                delegation("5", "1", v1, DelegationStatus.EXITED, 70),
            )
        )
    }

    private fun delegation(
        id: String,
        tokenId: String,
        validator: String,
        status: DelegationStatus,
        block: Long,
    ) =
        Delegation(
            id = id,
            validator = validator,
            tokenId = tokenId,
            owner = alice,
            status = status,
            tokenLevel = TokenLevel.Dawn,
            stakedAmount = "10",
            totalRewardsClaimed = BigInteger.ZERO,
            txId = "0x" + "f".repeat(64),
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
        )

    private fun ids(rows: List<StargateToken>) = rows.map { it.tokenId }

    @Test
    fun `findById is the current row or null`() {
        assertEquals(40L, repository.findById("1")?.blockNumber)
        assertEquals(Address.ZERO_ADDRESS, repository.findById("4")?.owner)
        assertNull(repository.findById("9"))
    }

    @Test
    fun `the unfiltered page skips burned tokens, newest first, and pages by offset`() {
        assertEquals(
            listOf("1", "2", "3"),
            ids(repository.findActive(null, null, Direction.DESC, 0, 10)),
        )
        assertEquals(listOf("3", "2"), ids(repository.findActive(null, null, Direction.ASC, 0, 2)))
        assertEquals(listOf("1"), ids(repository.findActive(null, null, Direction.ASC, 2, 2)))
    }

    @Test
    fun `owner and manager scope the page, together as either`() {
        assertEquals(
            listOf("1", "2"),
            ids(repository.findActive(alice, null, Direction.DESC, 0, 10)),
        )
        assertEquals(listOf("2"), ids(repository.findActive(null, bob, Direction.DESC, 0, 10)))
        assertEquals(listOf("2", "3"), ids(repository.findActive(bob, bob, Direction.DESC, 0, 10)))
        assertEquals(
            emptyList<String>(),
            ids(repository.findActive(carol, null, Direction.DESC, 0, 10)),
        )
    }

    @Test
    fun `each token carries its latest live delegation, an exited one reading as NONE`() {
        val page =
            repository.findActive(null, null, Direction.DESC, 0, 10).associateBy { it.tokenId }

        assertEquals(
            Status.ACTIVE to v2,
            page.getValue("1").let { it.delegationStatus to it.validatorId },
        )
        assertEquals(
            Status.NONE to null,
            page.getValue("2").let { it.delegationStatus to it.validatorId },
        )
        assertEquals(
            Status.QUEUED to v1,
            page.getValue("3").let { it.delegationStatus to it.validatorId },
        )
        assertEquals(Status.NONE, repository.findById("4")?.delegationStatus)
        assertEquals(v2, repository.findById("1")?.validatorId)
    }
}
