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

class B3trChallengesServiceTest {
    private val repository: B3trChallengeRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val inlineVersioningProperties: InlineVersioningProperties = mockk()

    private lateinit var service: B3trChallengesService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10_000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { repository.findById(any()) } returns java.util.Optional.empty()
        every { repository.getLatestRecord() } returns null
        service =
            B3trChallengesService(
                repository = repository,
                mongoTemplate = mongoTemplate,
                inlineVersioningProperties = inlineVersioningProperties,
            )
    }

    @Test
    fun `processEvents creates challenge from events and tracks claims`() {
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()

        val createEvent =
            challengeCreatedEvent(
                id = "create",
                txId = "0xcreate",
                challengeId = 1L,
                creator = "0x0000000000000000000000000000000000000abc",
                selectedApps = listOf("0xapp1"),
            )
        val inviteEvent =
            challengeEvent(
                eventType = "ChallengeInviteAdded",
                id = "invite",
                txId = "0xinvite",
                challengeId = 1L,
                returnValues = mapOf("invitee" to "0x0000000000000000000000000000000000000def"),
            )
        val completedEvent =
            challengeEvent(
                eventType = "ChallengeCompleted",
                id = "complete",
                txId = "0xcomplete",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "settlementMode" to SettlementMode.TopWinners.ordinal,
                        "bestScore" to BigInteger.valueOf(10),
                        "bestCount" to 1,
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
            runBlocking {
                service.processEvents(listOf(createEvent, inviteEvent, completedEvent, payoutEvent))
            }

        assertEquals(1, updated.size)
        assertEquals(0, archived.size)
        val challenge = updated.single()
        assertEquals(1, challenge.version)
        assertEquals("0xcreate", challenge.createdTxId)
        assertEquals(100L, challenge.createdAtBlockNumber)
        assertEquals(ChallengeStatus.Completed, challenge.status)
        assertEquals(SettlementMode.TopWinners, challenge.settlementMode)
        assertEquals("Spring Sprint", challenge.title)
        assertEquals("", challenge.description)
        assertEquals("", challenge.imageURI)
        assertEquals("", challenge.metadataURI)
        assertEquals(BigInteger.TEN, challenge.stakeAmount)
        assertEquals(5, challenge.startRound)
        assertEquals(6, challenge.endRound)
        assertEquals(2, challenge.duration)
        assertEquals(false, challenge.allApps)
        assertEquals(BigInteger.TEN, challenge.totalPrize)
        assertEquals(1, challenge.participantCount)
        assertEquals(1, challenge.invitedCount)
        assertEquals(1, challenge.selectedAppsCount)
        assertEquals(1, challenge.payoutsClaimed)
        assertIterableEquals(
            listOf("0x0000000000000000000000000000000000000abc"),
            challenge.participants,
        )
        assertIterableEquals(
            listOf("0x0000000000000000000000000000000000000def"),
            challenge.invited,
        )
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
    fun `processEvents replays lifecycle updates without contract reads`() {
        val existing =
            challenge(
                version = 2,
                participants =
                    listOf(
                        "0x0000000000000000000000000000000000000abc",
                        "0x0000000000000000000000000000000000000001",
                    ),
                invited = listOf("0x0000000000000000000000000000000000000002"),
                declined = listOf("0x0000000000000000000000000000000000000003"),
                eligibleInvitees =
                    listOf(
                        "0x0000000000000000000000000000000000000001",
                        "0x0000000000000000000000000000000000000002",
                        "0x0000000000000000000000000000000000000003",
                    ),
                totalPrize = BigInteger.valueOf(20),
            )
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(existing)

        val joinEvent =
            challengeEvent(
                eventType = "ChallengeJoined",
                id = "join",
                txId = "0xjoin",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "challengeId" to BigInteger.ONE,
                        "participant" to "0x0000000000000000000000000000000000000002",
                    ),
            )
        val leaveEvent =
            challengeEvent(
                eventType = "ChallengeLeft",
                id = "leave",
                txId = "0xleave",
                challengeId = 1L,
                returnValues =
                    mapOf(
                        "challengeId" to BigInteger.ONE,
                        "participant" to "0x0000000000000000000000000000000000000001",
                    ),
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
                        "invitee" to "0x0000000000000000000000000000000000000003",
                    ),
            )
        val cancelEvent =
            challengeEvent(
                eventType = "ChallengeCancelled",
                id = "cancel",
                txId = "0xcancel",
                challengeId = 1L,
                returnValues = mapOf("challengeId" to BigInteger.ONE),
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
                        "account" to "0x0000000000000000000000000000000000000abc",
                    ),
            )

        val (updated, archived) =
            runBlocking {
                service.processEvents(
                    listOf(joinEvent, leaveEvent, inviteEvent, cancelEvent, refundEvent)
                )
            }

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        val challenge = updated.single()
        assertEquals(3, challenge.version)
        assertEquals(ChallengeStatus.Cancelled, challenge.status)
        assertEquals(BigInteger.valueOf(20), challenge.totalPrize)
        assertIterableEquals(
            listOf(
                "0x0000000000000000000000000000000000000abc",
                "0x0000000000000000000000000000000000000002",
            ),
            challenge.participants,
        )
        assertIterableEquals(
            listOf(
                "0x0000000000000000000000000000000000000001",
                "0x0000000000000000000000000000000000000003",
            ),
            challenge.invited,
        )
        assertIterableEquals(emptyList<String>(), challenge.declined)
        assertIterableEquals(
            listOf(
                "0x0000000000000000000000000000000000000001",
                "0x0000000000000000000000000000000000000002",
                "0x0000000000000000000000000000000000000003",
            ),
            challenge.eligibleInvitees,
        )
        assertIterableEquals(
            listOf("0x0000000000000000000000000000000000000abc"),
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

    private fun challengeCreatedEvent(
        id: String,
        txId: String,
        challengeId: Long,
        creator: String = "0x0000000000000000000000000000000000000abc",
        kind: ChallengeKind = ChallengeKind.Stake,
        visibility: ChallengeVisibility = ChallengeVisibility.Private,
        challengeType: ChallengeType = ChallengeType.MaxActions,
        title: String = "Spring Sprint",
        description: String = "",
        imageURI: String = "",
        metadataURI: String = "",
        stakeAmount: BigInteger = BigInteger.TEN,
        startRound: Int = 5,
        endRound: Int = 6,
        threshold: BigInteger = BigInteger.ZERO,
        allApps: Boolean = false,
        selectedApps: List<String> = listOf("0xapp1"),
    ) =
        challengeEvent(
            eventType = "ChallengeCreated",
            id = id,
            txId = txId,
            challengeId = challengeId,
            returnValues =
                mapOf(
                    "creator" to creator,
                    "endRound" to endRound,
                    "kind" to kind.ordinal,
                    "visibility" to visibility.ordinal,
                    "challengeType" to challengeType.ordinal,
                    "title" to title,
                    "description" to description,
                    "imageURI" to imageURI,
                    "metadataURI" to metadataURI,
                    "stakeAmount" to stakeAmount,
                    "startRound" to startRound,
                    "threshold" to threshold,
                    "allApps" to allApps,
                    "selectedApps" to selectedApps,
                ),
        )

    private fun challenge(
        version: Int,
        participants: List<String>,
        invited: List<String>,
        declined: List<String>,
        eligibleInvitees: List<String>,
        totalPrize: BigInteger,
    ) =
        B3trChallenge(
            version = version,
            blockId = "0xold",
            blockNumber = 90L,
            blockTimestamp = 900L,
            challengeId = 1L,
            kind = ChallengeKind.Stake,
            visibility = ChallengeVisibility.Private,
            challengeType = ChallengeType.MaxActions,
            status = ChallengeStatus.Pending,
            lifecycleStatus = ChallengeStatus.Pending,
            phase = ChallengePhase.Upcoming,
            settlementMode = SettlementMode.None,
            creator = "0x0000000000000000000000000000000000000abc",
            title = "Spring Sprint",
            description = "",
            imageURI = "",
            metadataURI = "",
            stakeAmount = BigInteger.TEN,
            startRound = 5,
            endRound = 6,
            duration = 2,
            threshold = BigInteger.ZERO,
            numWinners = 0,
            winnersClaimed = 0,
            prizePerWinner = BigInteger.ZERO,
            allApps = false,
            totalPrize = totalPrize,
            participantCount = participants.size,
            invitedCount = invited.size,
            declinedCount = declined.size,
            selectedAppsCount = 1,
            winnersCount = 0,
            bestScore = BigInteger.ZERO,
            bestCount = 0,
            payoutsClaimed = 0,
            participants = participants,
            invited = invited,
            declined = declined,
            selectedApps = listOf("0xapp1"),
            winners = emptyList<String>(),
            eligibleInvitees = eligibleInvitees,
            claimedBy = emptyList<String>(),
            refundedBy = emptyList<String>(),
            creatorRefunded = false,
            createdAtBlockNumber = 80L,
            createdAtBlockTimestamp = 800L,
            createdTxId = "0xcreated",
            currentRound = 0,
            maxParticipants = 100,
        )
}
