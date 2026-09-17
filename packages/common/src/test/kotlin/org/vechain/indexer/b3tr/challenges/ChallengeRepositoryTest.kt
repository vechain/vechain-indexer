package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChallengeRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ChallengeWriteRepository
    private lateinit var reader: ChallengeReadRepository

    private val creator = "0x" + "aa".repeat(20)
    private val joiner = "0x" + "bb".repeat(20)
    private val invitee = "0x" + "cc".repeat(20)

    @BeforeAll
    fun start() {
        database.start()
        writer = ChallengeWriteRepository(database.jdbc)
        reader = ChallengeReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /**
     * Challenge 1 is public and active: the creator staked it, one wallet joined at block 20 and
     * one invitee declined. Challenge 2 is private and still pending.
     */
    private fun seed() {
        writer.save(
            ChallengeUpdate(
                challenges =
                    listOf(
                        challenge(1, 10),
                        challenge(2, 12, visibility = ChallengeVisibility.Private),
                        challenge(1, 20, status = ChallengeStatus.Active),
                    ),
                members =
                    listOf(
                        member(1, creator, ChallengeMemberRole.PARTICIPANT, 10),
                        member(1, invitee, ChallengeMemberRole.INVITED, 10),
                        member(1, invitee, ChallengeMemberRole.ELIGIBLE_INVITEE, 10),
                        member(1, joiner, ChallengeMemberRole.PARTICIPANT, 20),
                        member(1, invitee, ChallengeMemberRole.INVITED, 20, present = false),
                        member(1, invitee, ChallengeMemberRole.DECLINED, 20),
                    ),
                apps = listOf(ChallengeApps(1, 10, listOf("app-one", "app-two"))),
                userChallenges =
                    listOf(
                        user(creator, 1, 10, isCreator = true, status = ParticipantStatus.Joined),
                        user(invitee, 1, 10, status = ParticipantStatus.Invited),
                        user(joiner, 1, 20, status = ParticipantStatus.Joined),
                        user(invitee, 1, 20, status = ParticipantStatus.Declined),
                    ),
            )
        )
    }

    private fun blockId(block: Long) = "0x" + block.toString(16).padStart(64, '0')

    private fun challenge(
        challengeId: Long,
        block: Long,
        status: ChallengeStatus = ChallengeStatus.Pending,
        visibility: ChallengeVisibility = ChallengeVisibility.Public,
    ) =
        B3trChallenge(
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            challengeId = challengeId,
            kind = ChallengeKind.Stake,
            visibility = visibility,
            challengeType = ChallengeType.MaxActions,
            onChainStatus = status,
            status = status,
            settlementMode = SettlementMode.None,
            creator = creator,
            title = "challenge $challengeId",
            description = "a description",
            imageURI = "ipfs://image",
            metadataURI = "ipfs://meta",
            stakeAmount = BigInteger.TEN,
            startRound = 5,
            endRound = 6,
            duration = 2,
            threshold = BigInteger.ZERO,
            numWinners = 0,
            winnersClaimed = 0,
            prizePerWinner = BigInteger.ZERO,
            allApps = false,
            totalPrize = BigInteger.TEN,
            bestScore = BigInteger.ZERO,
            bestCount = 0,
            payoutsClaimed = 0,
            creatorRefunded = false,
            endRoundPassed = false,
            createdAtBlockNumber = 10,
            createdAtBlockTimestamp = 1000 + challengeId,
            createdTxId = "0x" + "dd".repeat(32),
        )

    private fun member(
        challengeId: Long,
        wallet: String,
        role: ChallengeMemberRole,
        block: Long,
        present: Boolean = true,
    ) = ChallengeMember(challengeId, wallet, role, block, present)

    private fun user(
        wallet: String,
        challengeId: Long,
        block: Long,
        isCreator: Boolean = false,
        status: ParticipantStatus = ParticipantStatus.None,
    ) =
        B3trUserChallenge(
            blockId = blockId(block),
            blockNumber = block,
            blockTimestamp = block * 100,
            wallet = wallet,
            challengeId = challengeId,
            challengeCreatedAtBlockTimestamp = 1000 + challengeId,
            participantStatus = status,
            isCreator = isCreator,
        )

    @Test
    fun `a challenge round-trips with the counts its members give it`() {
        val detail = reader.findById(1)!!

        assertEquals(20L, detail.blockNumber)
        assertEquals(ChallengeStatus.Active, detail.status)
        assertEquals("challenge 1", detail.title)
        assertEquals(BigInteger.TEN, detail.stakeAmount)
        assertEquals(2, detail.duration)
        assertEquals(listOf(creator, joiner), detail.participants)
        assertEquals(2, detail.participantCount)
        assertEquals(emptyList<String>(), detail.invited)
        assertEquals(listOf(invitee), detail.declined)
        assertEquals(listOf(invitee), detail.eligibleInvitees)
        assertEquals(listOf("app-one", "app-two"), detail.selectedApps)
        assertEquals(2, detail.selectedAppsCount)
        assertNull(reader.findById(99))
    }

    @Test
    fun `the public list sees only public challenges, newest first`() {
        assertEquals(
            listOf(1L),
            reader.findPublic(null, 0, 10, Direction.DESC).map { it.challengeId },
        )
        assertEquals(
            listOf(1L),
            reader.findPublic(ChallengeStatus.Active, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
        assertEquals(
            emptyList<Long>(),
            reader.findPublic(ChallengeStatus.Pending, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
    }

    @Test
    fun `a wallet's buckets follow what it is to the challenge`() {
        assertEquals(
            listOf(1L),
            reader.findByFilter(creator, ChallengeFilter.MyChallenges, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
        assertEquals(
            emptyList<Long>(),
            reader.findByFilter(invitee, ChallengeFilter.MyChallenges, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
        // The invitee declined, so the challenge is theirs to find again under History.
        assertEquals(
            listOf(1L),
            reader.findByFilter(invitee, ChallengeFilter.History, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
        assertEquals(
            emptyList<Long>(),
            reader.findByFilter(joiner, ChallengeFilter.NeededAction, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
    }

    @Test
    fun `an open bucket skips the wallets already in the challenge`() {
        val stranger = "0x" + "ee".repeat(20)

        assertEquals(
            listOf(1L),
            reader.findOpenTo(stranger, ChallengeStatus.Active, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
        assertEquals(
            emptyList<Long>(),
            reader.findOpenTo(creator, ChallengeStatus.Active, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
        // A declined invitee still holds a status on the challenge, so it is not open to them.
        assertEquals(
            emptyList<Long>(),
            reader.findOpenTo(invitee, ChallengeStatus.Active, 0, 10, Direction.DESC).map {
                it.challengeId
            },
        )
    }

    @Test
    fun `the indexer reads the current challenge, its members and the wallets in it`() {
        assertEquals(
            listOf(1L to 20L, 2L to 12L),
            writer
                .findCurrent(setOf(1, 2))
                .map { it.challengeId to it.blockNumber }
                .sortedBy {
                    it.first
                },
        )
        val members = writer.findCurrentMembers(setOf(1))[1]!!
        assertEquals(listOf(creator, joiner), members[ChallengeMemberRole.PARTICIPANT])
        assertNull(members[ChallengeMemberRole.INVITED])
        assertEquals(listOf(invitee), members[ChallengeMemberRole.DECLINED])

        assertEquals(
            listOf(ParticipantStatus.Declined),
            writer.findCurrentUsers(setOf(invitee to 1L)).map { it.participantStatus },
        )
        assertEquals(3, writer.findCurrentUsersOfChallenge(setOf(1)).size)
        val byRound = writer.findCurrentByRounds(4, 5)
        assertEquals(listOf(1L, 2L), byRound.map { it.challengeId })
        assertEquals(listOf(2, 0), byRound.map { it.participantCount })
    }

    @Test
    fun `the indexer's own reads still work with the deferrable indexes dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(ChallengeIndexes.SET)
        try {
            writer.save(
                ChallengeUpdate(
                    challenges = listOf(challenge(9, 90)),
                    members = listOf(member(9, creator, ChallengeMemberRole.PARTICIPANT, 90)),
                    userChallenges = listOf(user(creator, 9, 90, isCreator = true)),
                )
            )

            assertEquals(listOf(9L), writer.findCurrent(setOf(9)).map { it.challengeId })
            assertEquals(
                listOf(creator),
                writer.findCurrentMembers(setOf(9))[9]!![ChallengeMemberRole.PARTICIPANT],
            )
            assertEquals(1, writer.findCurrentUsersOfChallenge(setOf(9)).size)
            assertEquals(1, writer.findCurrentUsers(setOf(creator to 9L)).size)
            assertTrue(writer.findCurrentByRounds(4, 5).any { it.challengeId == 9L })

            writer.rollbackFrom(90)
            assertEquals(emptyList<Long>(), writer.findCurrent(setOf(9)).map { it.challengeId })
        } finally {
            builder.build(ChallengeIndexes.SET)
        }
    }

    @Test
    fun `rollback reopens the rows the block superseded`() {
        writer.rollbackFrom(20)

        val detail = reader.findById(1)!!
        assertEquals(10L, detail.blockNumber)
        assertEquals(ChallengeStatus.Pending, detail.status)
        assertEquals(listOf(creator), detail.participants)
        assertEquals(listOf(invitee), detail.invited)
        assertEquals(emptyList<String>(), detail.declined)
        assertEquals(
            listOf(ParticipantStatus.Invited),
            writer.findCurrentUsers(setOf(invitee to 1L)).map { it.participantStatus },
        )

        writer.truncate()
        assertEquals(0L, reader.latestBlockNumber())
        assertEquals(0L, reader.latestUserBlockNumber())
        seed()
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(20))
        // The challenge row, the invite that ended and the invitee's row before they declined.
        assertEquals(3, writer.prune(21))
        assertEquals(20L, reader.findById(1)?.blockNumber)
        writer.truncate()
        seed()
    }
}
