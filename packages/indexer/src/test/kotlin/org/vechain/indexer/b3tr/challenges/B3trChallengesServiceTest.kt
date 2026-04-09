package org.vechain.indexer.b3tr.challenges

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.client.ThorClient

class B3trChallengesServiceTest {
    private val repository: B3trChallengeRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val inlineVersioningProperties: InlineVersioningProperties = mockk()
    private val thorClient: ThorClient = mockk()

    private lateinit var service: TestableB3trChallengesService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10_000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { repository.findById(any()) } returns java.util.Optional.empty()
        service =
            TestableB3trChallengesService(
                repository = repository,
                mongoTemplate = mongoTemplate,
                inlineVersioningProperties = inlineVersioningProperties,
                thorClient = thorClient,
            )
    }

    @Test
    fun `processEvents creates challenge snapshot and tracks claims`() {
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()

        service.snapshots[1L] =
            snapshot(
                creator = "0x0000000000000000000000000000000000000abc",
                participants = listOf("0x0000000000000000000000000000000000000abc"),
                invited = listOf("0x0000000000000000000000000000000000000def"),
                selectedApps = listOf("0xapp1"),
            )

        val createEvent =
            challengeEvent(
                eventType = "ChallengeCreated",
                id = "create",
                txId = "0xcreate",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "challengeId" to BigInteger.ONE,
                        "creator" to "0x0000000000000000000000000000000000000abc",
                    ),
            )
        val payoutEvent =
            challengeEvent(
                eventType = "ChallengePayoutClaimed",
                id = "claim",
                txId = "0xclaim",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "challengeId" to BigInteger.ONE,
                        "account" to "0x0000000000000000000000000000000000000abc",
                    ),
            )

        val (updated, archived) =
            runBlocking { service.processEvents(listOf(createEvent, payoutEvent)) }

        assertEquals(1, updated.size)
        assertEquals(0, archived.size)
        val challenge = updated.single()
        assertEquals(1, challenge.version)
        assertEquals("0xcreate", challenge.createdTxId)
        assertEquals(100L, challenge.createdAtBlockNumber)
        assertEquals(1, challenge.participantCount)
        assertIterableEquals(
            listOf("0x0000000000000000000000000000000000000def"),
            challenge.eligibleInvitees,
        )
        assertIterableEquals(
            listOf("0x0000000000000000000000000000000000000abc"),
            challenge.claimedBy,
        )
        assertIterableEquals(listOf("0xapp1"), challenge.selectedApps)
    }

    @Test
    fun `processEvents merges tracked addresses on update`() {
        val existing =
            challenge(
                version = 2,
                eligibleInvitees = listOf("0x0000000000000000000000000000000000000001"),
                claimedBy = listOf("0x0000000000000000000000000000000000000002"),
            )
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(existing)

        service.snapshots[1L] =
            snapshot(
                invited = listOf("0x0000000000000000000000000000000000000003"),
                declined = listOf("0x0000000000000000000000000000000000000004"),
            )

        val inviteEvent =
            challengeEvent(
                eventType = "ChallengeInviteAdded",
                id = "invite",
                txId = "0xinvite",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "challengeId" to BigInteger.ONE,
                        "invitee" to "0x0000000000000000000000000000000000000005",
                    ),
            )
        val refundEvent =
            challengeEvent(
                eventType = "ChallengeRefundClaimed",
                id = "refund",
                txId = "0xrefund",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "challengeId" to BigInteger.ONE,
                        "account" to "0x0000000000000000000000000000000000000006",
                    ),
            )

        val (updated, archived) =
            runBlocking { service.processEvents(listOf(inviteEvent, refundEvent)) }

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        val challenge = updated.single()
        assertEquals(3, challenge.version)
        assertIterableEquals(
            listOf(
                "0x0000000000000000000000000000000000000001",
                "0x0000000000000000000000000000000000000003",
                "0x0000000000000000000000000000000000000004",
                "0x0000000000000000000000000000000000000005",
            ),
            challenge.eligibleInvitees,
        )
        assertIterableEquals(
            listOf("0x0000000000000000000000000000000000000002"),
            challenge.claimedBy,
        )
        assertIterableEquals(
            listOf("0x0000000000000000000000000000000000000006"),
            challenge.refundedBy,
        )
    }

    private fun challengeEvent(
        eventType: String,
        id: String,
        txId: String,
        challengeId: Long,
        returnValues: Map<String, Any>,
    ) =
        buildIndexedEvent(
            id = id,
            blockId = "0xblock",
            blockNumber = 100L,
            blockTimestamp = 1_000L,
            txId = txId,
            eventType = eventType,
            params =
                AbiEventParameters(
                    returnValues = returnValues + ("challengeId" to BigInteger.valueOf(challengeId))
                ),
        )

    private fun snapshot(
        creator: String = "0x0000000000000000000000000000000000000abc",
        participants: List<String> = listOf("0x0000000000000000000000000000000000000abc"),
        invited: List<String> = emptyList(),
        declined: List<String> = emptyList(),
        selectedApps: List<String> = emptyList(),
    ) =
        ChallengeContractSnapshot(
            kind = ChallengeKind.Stake,
            visibility = ChallengeVisibility.Private,
            thresholdMode = ThresholdMode.None,
            status = ChallengeStatus.Pending,
            settlementMode = SettlementMode.None,
            creator = creator,
            stakeAmount = BigInteger.TEN,
            startRound = 5,
            endRound = 6,
            duration = 2,
            threshold = BigInteger.ZERO,
            allApps = selectedApps.isEmpty(),
            totalPrize = BigInteger.TEN,
            participantCount = participants.size,
            invitedCount = invited.size,
            declinedCount = declined.size,
            selectedAppsCount = selectedApps.size,
            bestScore = BigInteger.ZERO,
            bestCount = 0,
            qualifiedCount = 0,
            payoutsClaimed = 0,
            participants = participants,
            invited = invited,
            declined = declined,
            selectedApps = selectedApps,
        )

    private fun challenge(version: Int, eligibleInvitees: List<String>, claimedBy: List<String>) =
        B3trChallenge(
            version = version,
            blockId = "0xold",
            blockNumber = 90L,
            blockTimestamp = 900L,
            challengeId = 1L,
            kind = ChallengeKind.Stake,
            visibility = ChallengeVisibility.Private,
            thresholdMode = ThresholdMode.None,
            status = ChallengeStatus.Pending,
            settlementMode = SettlementMode.None,
            creator = "0x0000000000000000000000000000000000000abc",
            stakeAmount = BigInteger.TEN,
            startRound = 5,
            endRound = 6,
            duration = 2,
            threshold = BigInteger.ZERO,
            allApps = false,
            totalPrize = BigInteger.TEN,
            participantCount = 1,
            invitedCount = 0,
            declinedCount = 0,
            selectedAppsCount = 1,
            bestScore = BigInteger.ZERO,
            bestCount = 0,
            qualifiedCount = 0,
            payoutsClaimed = 0,
            participants = listOf("0x0000000000000000000000000000000000000abc"),
            invited = emptyList(),
            declined = emptyList(),
            selectedApps = listOf("0xapp1"),
            eligibleInvitees = eligibleInvitees,
            claimedBy = claimedBy,
            refundedBy = emptyList(),
            createdAtBlockNumber = 80L,
            createdAtBlockTimestamp = 800L,
            createdTxId = "0xcreated",
        )

    private class TestableB3trChallengesService(
        repository: B3trChallengeRepository,
        mongoTemplate: MongoTemplate,
        inlineVersioningProperties: InlineVersioningProperties,
        thorClient: ThorClient,
    ) :
        B3trChallengesService(
            repository = repository,
            mongoTemplate = mongoTemplate,
            inlineVersioningProperties = inlineVersioningProperties,
            thorClient = thorClient,
            challengesContract = "0xchallenge",
        ) {
        val snapshots = mutableMapOf<Long, ChallengeContractSnapshot>()

        override suspend fun fetchSnapshot(
            challengeId: Long,
            blockId: String,
        ): ChallengeContractSnapshot = snapshots.getValue(challengeId)
    }
}
