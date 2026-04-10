package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

class B3trChallengeEventUtilsTest {
    @Test
    fun `createChallengeState initializes stake challenge state`() {
        val state = createState()

        assertEquals(ChallengeKind.Stake, state.kind)
        assertEquals(ChallengeStatus.Pending, state.status)
        assertEquals(SettlementMode.None, state.settlementMode)
        assertEquals(BigInteger.TEN, state.stakeAmount)
        assertEquals(BigInteger.TEN, state.totalPrize)
        assertIterableEquals(listOf(CREATOR), state.participants)
        assertIterableEquals(listOf(APP_ID), state.selectedApps)
        assertEquals(100L, state.createdAtBlockNumber)
        assertEquals(1_000L, state.createdAtBlockTimestamp)
        assertEquals("0xcreate", state.createdTxId)
    }

    @Test
    fun `applyEvent invite removes decline without duplicating eligibility`() {
        val state = createState()
        state.declined.add(PARTICIPANT)
        state.eligibleInvitees.add(PARTICIPANT)

        B3trChallengeEventUtils.applyEvent(
            1L,
            state,
            challengeEvent(
                eventType = "ChallengeInviteAdded",
                id = "invite",
                returnValues = mapOf("invitee" to PARTICIPANT),
            ),
        )

        assertIterableEquals(listOf(PARTICIPANT), state.invited)
        assertIterableEquals(emptyList<String>(), state.declined)
        assertIterableEquals(listOf(PARTICIPANT), state.eligibleInvitees)
    }

    @Test
    fun `applyEvent join moves participant and updates prize once`() {
        val state = createState()
        state.invited.add(PARTICIPANT)
        state.declined.add(PARTICIPANT)

        val joinEvent =
            challengeEvent(
                eventType = "ChallengeJoined",
                id = "join",
                returnValues = mapOf("participant" to PARTICIPANT),
            )

        B3trChallengeEventUtils.applyEvent(1L, state, joinEvent)
        B3trChallengeEventUtils.applyEvent(1L, state, joinEvent)

        assertIterableEquals(listOf(CREATOR, PARTICIPANT), state.participants)
        assertIterableEquals(emptyList<String>(), state.invited)
        assertIterableEquals(emptyList<String>(), state.declined)
        assertEquals(BigInteger.valueOf(20), state.totalPrize)
    }

    @Test
    fun `applyEvent leave re-invites eligible participant and decrements prize`() {
        val state = createState()
        state.participants.add(PARTICIPANT)
        state.eligibleInvitees.add(PARTICIPANT)
        state.totalPrize = BigInteger.valueOf(20)

        B3trChallengeEventUtils.applyEvent(
            1L,
            state,
            challengeEvent(
                eventType = "ChallengeLeft",
                id = "leave",
                returnValues = mapOf("participant" to PARTICIPANT),
            ),
        )

        assertIterableEquals(listOf(CREATOR), state.participants)
        assertIterableEquals(listOf(PARTICIPANT), state.invited)
        assertEquals(BigInteger.TEN, state.totalPrize)
    }

    @Test
    fun `applyEvent decline removes participant and keeps eligibility`() {
        val state = createState()
        state.participants.add(PARTICIPANT)
        state.invited.add(PARTICIPANT)
        state.totalPrize = BigInteger.valueOf(20)

        B3trChallengeEventUtils.applyEvent(
            1L,
            state,
            challengeEvent(
                eventType = "ChallengeDeclined",
                id = "decline",
                returnValues = mapOf("participant" to PARTICIPANT),
            ),
        )

        assertIterableEquals(listOf(CREATOR), state.participants)
        assertIterableEquals(emptyList<String>(), state.invited)
        assertIterableEquals(listOf(PARTICIPANT), state.declined)
        assertIterableEquals(listOf(PARTICIPANT), state.eligibleInvitees)
        assertEquals(BigInteger.TEN, state.totalPrize)
    }

    @Test
    fun `applyEvent finalized updates settlement fields`() {
        val state = createState()

        B3trChallengeEventUtils.applyEvent(
            1L,
            state,
            challengeEvent(
                eventType = "ChallengeFinalized",
                id = "finalize",
                returnValues =
                    mapOf(
                        "settlementMode" to SettlementMode.TopWinners.ordinal,
                        "bestScore" to BigInteger.valueOf(42),
                        "bestCount" to 3,
                        "qualifiedCount" to 5,
                    ),
            ),
        )

        assertEquals(ChallengeStatus.Finalized, state.status)
        assertEquals(SettlementMode.TopWinners, state.settlementMode)
        assertEquals(BigInteger.valueOf(42), state.bestScore)
        assertEquals(3, state.bestCount)
        assertEquals(5, state.qualifiedCount)
    }

    @Test
    fun `applyEvent claim events keep address lists distinct`() {
        val state = createState()
        val payoutEvent =
            challengeEvent(
                eventType = "ChallengePayoutClaimed",
                id = "claim",
                returnValues = mapOf("account" to PARTICIPANT),
            )
        val refundEvent =
            challengeEvent(
                eventType = "ChallengeRefundClaimed",
                id = "refund",
                returnValues = mapOf("account" to PARTICIPANT),
            )

        B3trChallengeEventUtils.applyEvent(1L, state, payoutEvent)
        B3trChallengeEventUtils.applyEvent(1L, state, payoutEvent)
        B3trChallengeEventUtils.applyEvent(1L, state, refundEvent)
        B3trChallengeEventUtils.applyEvent(1L, state, refundEvent)

        assertIterableEquals(listOf(PARTICIPANT), state.claimedBy)
        assertEquals(2, state.payoutsClaimed)
        assertIterableEquals(listOf(PARTICIPANT), state.refundedBy)
    }

    private fun createState(kind: ChallengeKind = ChallengeKind.Stake): MutableChallengeState =
        B3trChallengeEventUtils.createChallengeState(
            challengeCreatedEvent(id = "create", txId = "0xcreate", challengeId = 1L, kind = kind)
        )

    private fun challengeEvent(
        eventType: String,
        id: String,
        challengeId: Long = 1L,
        txId: String = "0x$id",
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
        kind: ChallengeKind,
    ) =
        challengeEvent(
            eventType = "ChallengeCreated",
            id = id,
            challengeId = challengeId,
            txId = txId,
            returnValues =
                mapOf(
                    "creator" to CREATOR,
                    "endRound" to 6,
                    "kind" to kind.ordinal,
                    "visibility" to ChallengeVisibility.Private.ordinal,
                    "thresholdMode" to ThresholdMode.None.ordinal,
                    "stakeAmount" to BigInteger.TEN,
                    "startRound" to 5,
                    "threshold" to BigInteger.ZERO,
                    "allApps" to false,
                    "selectedApps" to listOf(APP_ID),
                ),
        )

    private companion object {
        const val APP_ID = "0xapp1"
        const val CREATOR = "0x0000000000000000000000000000000000000abc"
        const val PARTICIPANT = "0x0000000000000000000000000000000000000def"
    }
}
