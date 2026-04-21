package org.vechain.indexer.b3tr.challenges

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.b3tr.challenges.repository.B3trUserChallengeRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

class B3trUserChallengesServiceTest {
    private val repository: B3trUserChallengeRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val inlineVersioningProperties: InlineVersioningProperties = mockk()

    private lateinit var service: B3trUserChallengesService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10_000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { repository.findAllByChallengeId(any()) } returns emptyList()
        service =
            B3trUserChallengesService(
                repository = repository,
                mongoTemplate = mongoTemplate,
                inlineVersioningProperties = inlineVersioningProperties,
            )
    }

    @Test
    fun `processEvents creates wallet refs from creator invite join and decline events`() {
        val createEvent =
            challengeEvent(
                eventType = "ChallengeCreated",
                id = "create",
                challengeId = 1L,
                returnValues = mapOf("creator" to "0x0000000000000000000000000000000000000abc"),
            )
        val inviteEvent =
            challengeEvent(
                eventType = "ChallengeInviteAdded",
                id = "invite",
                challengeId = 1L,
                returnValues = mapOf("invitee" to "0x0000000000000000000000000000000000000def"),
            )
        val joinEvent =
            challengeEvent(
                eventType = "ChallengeJoined",
                id = "join",
                challengeId = 1L,
                returnValues = mapOf("participant" to "0x0000000000000000000000000000000000000123"),
            )
        val declineEvent =
            challengeEvent(
                eventType = "ChallengeDeclined",
                id = "decline",
                challengeId = 1L,
                returnValues = mapOf("participant" to "0x0000000000000000000000000000000000000456"),
            )

        val (updated, archived) =
            runBlocking {
                service.processEvents(listOf(createEvent, inviteEvent, joinEvent, declineEvent))
            }

        assertEquals(4, updated.size)
        assertEquals(0, archived.size)
        assertIterableEquals(
            listOf(
                "0x0000000000000000000000000000000000000abc",
                "0x0000000000000000000000000000000000000def",
                "0x0000000000000000000000000000000000000123",
                "0x0000000000000000000000000000000000000456",
            ),
            updated.map(B3trUserChallenge::wallet),
        )
        assertEquals(
            1L,
            updated
                .single { it.wallet == "0x0000000000000000000000000000000000000456" }
                .challengeCreatedAtBlockTimestamp,
        )
    }

    @Test
    fun `processEvents keeps stable refs when wallet challenge already exists`() {
        every { repository.findAllByChallengeId(1L) } returns
            listOf(
                B3trUserChallenge(
                    version = 1,
                    blockId = "0xexisting",
                    blockNumber = 10L,
                    blockTimestamp = 10L,
                    wallet = "0x0000000000000000000000000000000000000abc",
                    challengeId = 1L,
                    challengeCreatedAtBlockTimestamp = 1L,
                )
            )

        val createEvent =
            challengeEvent(
                eventType = "ChallengeCreated",
                id = "create",
                challengeId = 1L,
                returnValues = mapOf("creator" to "0x0000000000000000000000000000000000000abc"),
            )
        val joinEvent =
            challengeEvent(
                eventType = "ChallengeJoined",
                id = "join",
                challengeId = 1L,
                returnValues = mapOf("participant" to "0x0000000000000000000000000000000000000abc"),
            )

        val (updated, archived) =
            runBlocking { service.processEvents(listOf(createEvent, joinEvent)) }

        assertEquals(emptyList<B3trUserChallenge>(), updated)
        assertEquals(emptyList<B3trUserChallenge>(), archived)
    }

    @Test
    fun `processEvents ignores non-edge events including action rewards`() {
        val actionRewardEvent =
            buildIndexedEvent(
                id = "reward",
                blockId = "0xblock",
                blockNumber = 100L,
                blockTimestamp = 1_000L,
                txId = "0xtx",
                eventType = "B3TR_ActionReward",
                params = AbiEventParameters(returnValues = mapOf("receiver" to "0xabc")),
            )

        val (updated, archived) = runBlocking { service.processEvents(listOf(actionRewardEvent)) }

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
            blockId = "0xblock",
            blockNumber = 100L,
            blockTimestamp = 1L,
            txId = "0xtx-$id",
            eventType = eventType,
            params =
                AbiEventParameters(returnValues = returnValues + ("challengeId" to challengeId)),
        )
}
