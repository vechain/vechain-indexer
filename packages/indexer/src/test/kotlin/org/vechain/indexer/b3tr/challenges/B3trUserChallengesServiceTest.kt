package org.vechain.indexer.b3tr.challenges

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.b3tr.challenges.repository.B3trUserChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult

class B3trUserChallengesServiceTest {
    private val repository: B3trUserChallengeRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val inlineVersioningProperties: InlineVersioningProperties = mockk()
    private val thorClient: ThorClient = mockk()

    private lateinit var service: B3trUserChallengesService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10_000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { repository.findAllByChallengeId(any()) } returns emptyList()
        every { repository.findByWalletAndChallengeId(any(), any()) } returns null
        every { repository.findById(any<String>()) } returns java.util.Optional.empty()
        service =
            B3trUserChallengesService(
                repository = repository,
                mongoTemplate = mongoTemplate,
                inlineVersioningProperties = inlineVersioningProperties,
                thorClient = thorClient,
                challengesContractAddress = "0x892c882bb59df89b381530230b0bbc370cfe9a96",
            )
    }

    @Test
    fun `ChallengeCreated on Stake kind auto-joins the creator`() {
        val event =
            challengeEvent(
                eventType = "ChallengeCreated",
                id = "create",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "creator" to ADDR_A,
                        "kind" to 0, // Stake
                    ),
            )

        val (updated, archived) = runBlocking { service.processEvents(listOf(event)) }

        assertEquals(1, updated.size)
        assertEquals(0, archived.size)
        val record = updated.single()
        assertEquals(ADDR_A, record.wallet)
        assertTrue(record.isCreator)
        assertEquals(ParticipantStatus.Joined, record.participantStatus)
    }

    @Test
    fun `ChallengeCreated on Sponsored kind does not auto-join the creator`() {
        val event =
            challengeEvent(
                eventType = "ChallengeCreated",
                id = "create",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "creator" to ADDR_A,
                        "kind" to 1, // Sponsored
                    ),
            )

        val (updated, _) = runBlocking { service.processEvents(listOf(event)) }

        val record = updated.single()
        assertTrue(record.isCreator)
        assertEquals(ParticipantStatus.None, record.participantStatus)
    }

    @Test
    fun `Invited then Joined in same block bumps version once and archives once`() {
        val invite =
            challengeEvent(
                eventType = "ChallengeInviteAdded",
                id = "invite",
                challengeId = 1L,
                returnValues = mapOf("invitee" to ADDR_B),
            )
        val join =
            challengeEvent(
                eventType = "ChallengeJoined",
                id = "join",
                challengeId = 1L,
                returnValues = mapOf("participant" to ADDR_B),
            )

        val (updated, archived) = runBlocking { service.processEvents(listOf(invite, join)) }

        assertEquals(1, updated.size)
        assertEquals(0, archived.size) // new record, nothing to archive
        val record = updated.single()
        assertEquals(ParticipantStatus.Joined, record.participantStatus)
        assertEquals(1, record.version)
    }

    @Test
    fun `re-invite of declined wallet moves state back to Invited`() {
        val declined =
            B3trUserChallenge(
                version = 1,
                blockId = "0xexisting",
                blockNumber = 10L,
                blockTimestamp = 10L,
                wallet = ADDR_B,
                challengeId = 1L,
                challengeCreatedAtBlockTimestamp = 1L,
                participantStatus = ParticipantStatus.Declined,
            )
        every { repository.findByWalletAndChallengeId(ADDR_B, 1L) } returns declined

        val invite =
            challengeEvent(
                eventType = "ChallengeInviteAdded",
                id = "invite2",
                challengeId = 1L,
                returnValues = mapOf("invitee" to ADDR_B),
            )

        val (updated, archived) = runBlocking { service.processEvents(listOf(invite)) }

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals(ParticipantStatus.Invited, updated.single().participantStatus)
        assertEquals(2, updated.single().version)
    }

    @Test
    fun `ChallengePayoutClaimed sets isWinner and hasClaimedPrize`() {
        val existing =
            B3trUserChallenge(
                version = 3,
                blockId = "0xexisting",
                blockNumber = 10L,
                blockTimestamp = 10L,
                wallet = ADDR_A,
                challengeId = 1L,
                challengeCreatedAtBlockTimestamp = 1L,
                participantStatus = ParticipantStatus.Joined,
            )
        every { repository.findByWalletAndChallengeId(ADDR_A, 1L) } returns existing

        val event =
            challengeEvent(
                eventType = "ChallengePayoutClaimed",
                id = "claim",
                challengeId = 1L,
                returnValues = mapOf("account" to ADDR_A),
            )

        val (updated, _) = runBlocking { service.processEvents(listOf(event)) }

        val record = updated.single()
        assertTrue(record.isWinner)
        assertTrue(record.hasClaimedPrize)
        assertEquals(ParticipantStatus.Joined, record.participantStatus)
    }

    @Test
    fun `SplitWinPrizeClaimed upserts a record for a wallet that never appeared before`() {
        val event =
            challengeEvent(
                eventType = "SplitWinPrizeClaimed",
                id = "split-claim",
                challengeId = 1L,
                returnValues = mapOf("winner" to ADDR_C),
            )

        val (updated, _) = runBlocking { service.processEvents(listOf(event)) }

        val record = updated.single()
        assertEquals(ADDR_C, record.wallet)
        assertTrue(record.isWinner)
        assertTrue(record.hasClaimedPrize)
        assertEquals(ParticipantStatus.Joined, record.participantStatus)
    }

    @Test
    fun `ChallengeRefundClaimed sets hasClaimedRefund`() {
        val event =
            challengeEvent(
                eventType = "ChallengeRefundClaimed",
                id = "refund",
                challengeId = 1L,
                returnValues = mapOf("account" to ADDR_A),
            )

        val (updated, _) = runBlocking { service.processEvents(listOf(event)) }

        assertTrue(updated.single().hasClaimedRefund)
    }

    @Test
    fun `SplitWinCreatorRefunded sets hasClaimedRefund`() {
        val event =
            challengeEvent(
                eventType = "SplitWinCreatorRefunded",
                id = "creator-refund",
                challengeId = 1L,
                returnValues = mapOf("creator" to ADDR_A),
            )

        val (updated, _) = runBlocking { service.processEvents(listOf(event)) }

        assertTrue(updated.single().hasClaimedRefund)
    }

    @Test
    fun `ChallengeCompleted MaxActions marks participants whose score equals bestScore as winners`() {
        val winner =
            B3trUserChallenge(
                version = 1,
                blockId = "0xexisting",
                blockNumber = 10L,
                blockTimestamp = 10L,
                wallet = ADDR_A,
                challengeId = 1L,
                challengeCreatedAtBlockTimestamp = 1L,
                participantStatus = ParticipantStatus.Joined,
            )
        val nonWinner =
            B3trUserChallenge(
                version = 1,
                blockId = "0xexisting",
                blockNumber = 10L,
                blockTimestamp = 10L,
                wallet = ADDR_B,
                challengeId = 1L,
                challengeCreatedAtBlockTimestamp = 1L,
                participantStatus = ParticipantStatus.Joined,
            )
        every { repository.findAllByChallengeId(1L) } returns listOf(winner, nonWinner)

        val bestScore = BigInteger.valueOf(7)
        val clausesSlot = slot<List<Clause>>()
        val revisionSlot = slot<BlockRevision>()
        coEvery { thorClient.inspectClauses(capture(clausesSlot), capture(revisionSlot)) } answers
            {
                firstArg<List<Clause>>().map { clause ->
                    val returns =
                        if (clause.toString().contains(ADDR_A.removePrefix("0x"))) {
                            bestScore
                        } else {
                            BigInteger.valueOf(3)
                        }
                    inspectionResult(encodeUint256(returns))
                }
            }

        val event =
            challengeEvent(
                eventType = "ChallengeCompleted",
                id = "complete",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "settlementMode" to 1, // TopWinners
                        "bestScore" to bestScore,
                        "bestCount" to 1,
                    ),
            )

        val (updated, archived) = runBlocking { service.processEvents(listOf(event)) }

        val winnerUpdated = updated.single { it.wallet == ADDR_A }
        assertTrue(winnerUpdated.isWinner)
        val nonWinnerPresent = updated.any { it.wallet == ADDR_B }
        assertFalse(nonWinnerPresent) // non-winner's record didn't change; not in updated set
        assertEquals(1, archived.size) // winner's prior version archived
        assertEquals(2, clausesSlot.captured.size)
        assertEquals(BlockRevision.Id(BLOCK_ID), revisionSlot.captured)
        coVerify(exactly = 1) { thorClient.inspectClauses(any<List<Clause>>(), any()) }
    }

    @Test
    fun `ChallengeCompleted SplitWinCompleted is a no-op for per-user state`() {
        val participant =
            B3trUserChallenge(
                version = 1,
                blockId = "0xexisting",
                blockNumber = 10L,
                blockTimestamp = 10L,
                wallet = ADDR_A,
                challengeId = 1L,
                challengeCreatedAtBlockTimestamp = 1L,
                participantStatus = ParticipantStatus.Joined,
            )
        every { repository.findAllByChallengeId(1L) } returns listOf(participant)

        val event =
            challengeEvent(
                eventType = "ChallengeCompleted",
                id = "complete-split",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "settlementMode" to 3, // SplitWinCompleted
                        "bestScore" to BigInteger.ZERO,
                        "bestCount" to 0,
                    ),
            )

        val (updated, archived) = runBlocking { service.processEvents(listOf(event)) }

        assertEquals(emptyList<B3trUserChallenge>(), updated)
        assertEquals(emptyList<B3trUserChallenge>(), archived)
    }

    @Test
    fun `processEvents ignores non-tracked events`() {
        val event =
            buildIndexedEvent(
                id = "reward",
                blockId = BLOCK_ID,
                blockNumber = 100L,
                blockTimestamp = 1_000L,
                txId = "0xtx",
                eventType = "B3TR_ActionReward",
                params = AbiEventParameters(returnValues = mapOf("receiver" to "0xabc")),
            )

        val (updated, archived) = runBlocking { service.processEvents(listOf(event)) }

        assertEquals(emptyList<B3trUserChallenge>(), updated)
        assertEquals(emptyList<B3trUserChallenge>(), archived)
    }

    private fun challengeEvent(
        eventType: String,
        id: String,
        challengeId: Long,
        returnValues: Map<String, Any>,
    ) =
        buildIndexedEvent(
            id = id,
            blockId = BLOCK_ID,
            blockNumber = 100L,
            blockTimestamp = 1L,
            txId = "0xtx-$id",
            eventType = eventType,
            params =
                AbiEventParameters(returnValues = returnValues + ("challengeId" to challengeId)),
        )

    private fun encodeUint256(value: BigInteger): String {
        val hex = value.toString(16).padStart(64, '0')
        return "0x$hex"
    }

    private fun inspectionResult(data: String): InspectionResult =
        InspectionResult(
            data = data,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0L,
            reverted = false,
            vmError = null,
        )

    private companion object {
        const val BLOCK_ID = "0x1111111111111111111111111111111111111111111111111111111111111111"
        const val ADDR_A = "0x0000000000000000000000000000000000000aaa"
        const val ADDR_B = "0x0000000000000000000000000000000000000bbb"
        const val ADDR_C = "0x0000000000000000000000000000000000000ccc"
    }
}
