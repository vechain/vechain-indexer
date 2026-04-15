package org.vechain.indexer.b3tr.challenges

import io.mockk.*
import java.math.BigInteger
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult

class ChallengesServiceTest {
    private val repository: B3trChallengeRepository = mockk()
    private val mongoTemplate: MongoTemplate = mockk()
    private val thorClient: ThorClient = mockk()

    private val service =
        ChallengesService(
            repository = repository,
            mongoTemplate = mongoTemplate,
            thorClient = thorClient,
            xAllocVotingContract = "0x0000000000000000000000000000000000000001",
            challengesContract = "0x0000000000000000000000000000000000000002",
        )

    private val viewerWallet = "0x0000000000000000000000000000000000000def"
    private val viewer = Address(viewerWallet)

    private val pageable =
        PageRequest.of(
            0,
            10,
            Sort.by(
                Sort.Direction.DESC,
                B3trChallenge::createdAtBlockTimestamp.name,
                B3trChallenge::challengeId.name,
            ),
        )

    private fun pageRequest(page: Int, size: Int) =
        PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Direction.DESC,
                B3trChallenge::createdAtBlockTimestamp.name,
                B3trChallenge::challengeId.name,
            ),
        )

    @Test
    fun `getChallenge recomputes invalid status from current round and participant count`() {
        stubUiRuntime(currentRound = 8, maxParticipants = 100)
        every { repository.findById(B3trChallenge.documentId(1L)) } returns
            Optional.of(challenge(participantCount = 1, startRound = 5))

        val result = service.getChallenge(1L)

        assertEquals(ChallengeStatus.Invalid, result.status)
        assertEquals(ParticipantStatus.None, result.viewerStatus)
        assertEquals(100, result.maxParticipants)
        assertFalse(result.canJoin)
    }

    @Test
    fun `getChallenge returns anonymous detail with guest defaults`() {
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { repository.findById(B3trChallenge.documentId(1L)) } returns
            Optional.of(
                challenge(
                    participantCount = 1,
                    startRound = 6,
                    participants = listOf(wallet("abc")),
                    selectedApps = listOf("0xapp1", "0xapp2"),
                    allApps = false,
                )
            )

        val result = service.getChallenge(1L)

        assertEquals(ChallengeStatus.Pending, result.status)
        assertEquals(ParticipantStatus.None, result.viewerStatus)
        assertFalse(result.isCreator)
        assertFalse(result.isJoined)
        assertTrue(result.canJoin)
        assertEquals(listOf("0xapp1", "0xapp2"), result.selectedApps)
    }

    @Test
    fun `getChallenge returns anonymous detail for invited challenge`() {
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { repository.findById(B3trChallenge.documentId(1L)) } returns
            Optional.of(
                challenge(
                    participantCount = 1,
                    startRound = 6,
                    visibility = ChallengeVisibility.Private,
                    invited = listOf(viewerWallet),
                )
            )

        val result = service.getChallenge(1L)

        assertEquals(ParticipantStatus.None, result.viewerStatus)
        assertFalse(result.isInvitationPending)
        assertFalse(result.canAccept)
        assertFalse(result.canDecline)
    }

    @Test
    fun `getChallenge returns anonymous detail for creator challenge`() {
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { repository.findById(B3trChallenge.documentId(1L)) } returns
            Optional.of(
                challenge(
                    participantCount = 1,
                    startRound = 6,
                    visibility = ChallengeVisibility.Private,
                    creator = viewerWallet,
                    participants = listOf(viewerWallet),
                    invited = listOf(wallet("123")),
                )
            )

        val result = service.getChallenge(1L)

        assertFalse(result.isCreator)
        assertFalse(result.isJoined)
        assertFalse(result.canCancel)
        assertFalse(result.canAddInvites)
        assertEquals(listOf(wallet("123")), result.invited)
    }

    @Test
    fun `getChallenge returns anonymous detail for finalized winner`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100, participantActions = 6)
        every { repository.findById(B3trChallenge.documentId(1L)) } returns
            Optional.of(
                challenge(
                    participantCount = 2,
                    startRound = 5,
                    kind = ChallengeKind.Sponsored,
                    status = ChallengeStatus.Finalized,
                    settlementMode = SettlementMode.QualifiedSplit,
                    threshold = BigInteger.valueOf(5),
                    creator = wallet("abc"),
                    participants = listOf(viewerWallet, wallet("123")),
                    totalPrize = BigInteger.valueOf(500),
                )
            )

        val result = service.getChallenge(1L)

        assertEquals(ParticipantStatus.None, result.viewerStatus)
        assertFalse(result.canClaim)
    }

    @Test
    fun `getChallenge returns anonymous detail for invalid challenge`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100)
        every { repository.findById(B3trChallenge.documentId(1L)) } returns
            Optional.of(
                challenge(
                    participantCount = 1,
                    startRound = 5,
                    kind = ChallengeKind.Stake,
                    status = ChallengeStatus.Invalid,
                    participants = listOf(viewerWallet),
                )
            )

        val result = service.getChallenge(1L)

        assertFalse(result.canRefund)
        assertFalse(result.canClaim)
    }

    @Test
    fun `getChallenge returns anonymous detail for challenge awaiting finalization`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100)
        every { repository.findById(B3trChallenge.documentId(1L)) } returns
            Optional.of(
                challenge(
                    participantCount = 2,
                    startRound = 5,
                    endRound = 6,
                    creator = viewerWallet,
                    participants = listOf(viewerWallet, wallet("123")),
                )
            )

        val result = service.getChallenge(1L)

        assertEquals(ChallengeStatus.Active, result.status)
        assertFalse(result.canFinalize)
    }

    @Test
    fun `getChallenge throws when challenge does not exist`() {
        every { repository.findById(B3trChallenge.documentId(99L)) } returns Optional.empty()

        assertThrows(ResourceNotFoundException::class.java) { service.getChallenge(99L) }
    }

    @Test
    fun `getNeededActionChallenges returns pending invitation with accept and decline actions`() {
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 1,
                    startRound = 6,
                    visibility = ChallengeVisibility.Private,
                    invited = listOf(viewerWallet),
                )
            )

        val result = service.getNeededActionChallenges(viewer, pageable)

        assertEquals(1, result.data.size)
        val item = result.data.single()
        assertEquals(ParticipantStatus.Invited, item.viewerStatus)
        assertTrue(item.isInvitationPending)
        assertTrue(item.canAccept)
        assertTrue(item.canDecline)
    }

    @Test
    fun `getChallengeHistory keeps declined reacceptable invitations out of needed actions`() {
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 1,
                    startRound = 6,
                    visibility = ChallengeVisibility.Private,
                    declined = listOf(viewerWallet),
                )
            )

        val history = service.getChallengeHistory(viewer, pageable)
        val neededActions = service.getNeededActionChallenges(viewer, pageable)

        assertEquals(1, history.data.size)
        val item = history.data.single()
        assertEquals(ParticipantStatus.Declined, item.viewerStatus)
        assertTrue(item.canAccept)
        assertFalse(item.canDecline)
        assertTrue(neededActions.data.isEmpty())
    }

    @Test
    fun `finalizable challenge appears in needed actions instead of active`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returnsMany
            listOf(
                listOf(
                    challenge(
                        participantCount = 2,
                        startRound = 5,
                        endRound = 6,
                        creator = viewerWallet,
                        participants = listOf(viewerWallet, wallet("123")),
                    )
                ),
                emptyList(),
            )

        val neededActions = service.getNeededActionChallenges(viewer, pageable)
        val active = service.getActiveChallenges(viewer, pageable)

        assertEquals(1, neededActions.data.size)
        assertTrue(neededActions.data.single().canFinalize)
        assertTrue(active.data.isEmpty())
    }

    @Test
    fun `getNeededActionChallenges excludes finalizable challenges for unrelated wallets`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 2,
                    startRound = 5,
                    endRound = 6,
                    creator = wallet("abc"),
                    participants = listOf(wallet("abc"), wallet("123")),
                )
            )

        val result = service.getNeededActionChallenges(viewer, pageable)

        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `getOpenChallenges returns joinable public pending challenges`() {
        val querySlot = slot<Query>()
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { mongoTemplate.find(capture(querySlot), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 1,
                    startRound = 6,
                    visibility = ChallengeVisibility.Public,
                    creator = wallet("abc"),
                    participants = listOf(wallet("abc")),
                )
            )

        val result = service.getOpenChallenges(viewer, pageable)

        assertEquals(1, result.data.size)
        val item = result.data.single()
        assertTrue(item.canJoin)
        assertEquals(100, item.maxParticipants)
        assertEquals("Spring Sprint", item.title)
        val queryObject = querySlot.captured.queryObject
        assertTrue(containsKey(queryObject, "participantCount"))
        assertTrue(containsKey(queryObject, "creator"))
    }

    @Test
    fun `getExploreChallenges returns public active challenges from other participants`() {
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 2,
                    startRound = 5,
                    visibility = ChallengeVisibility.Public,
                    creator = wallet("abc"),
                    participants = listOf(wallet("abc"), wallet("123")),
                )
            )

        val result = service.getExploreChallenges(viewer, pageable)

        assertEquals(1, result.data.size)
        val item = result.data.single()
        assertEquals(ChallengeStatus.Active, item.status)
        assertEquals(ParticipantStatus.None, item.viewerStatus)
        assertFalse(item.isCreator)
        assertFalse(item.isJoined)
    }

    @Test
    fun `getExploreChallenges excludes viewer created challenges`() {
        val querySlot = slot<Query>()
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { mongoTemplate.find(capture(querySlot), B3trChallenge::class.java) } returns
            emptyList()

        val result = service.getExploreChallenges(viewer, pageable)

        assertTrue(result.data.isEmpty())
        val queryObject = querySlot.captured.queryObject
        assertTrue(containsKey(queryObject, "creator"))
        assertTrue(containsValue(queryObject, viewerWallet))
    }

    @Test
    fun `getExploreChallenges excludes viewer joined challenges`() {
        val querySlot = slot<Query>()
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { mongoTemplate.find(capture(querySlot), B3trChallenge::class.java) } returns
            emptyList()

        val result = service.getExploreChallenges(viewer, pageable)

        assertTrue(result.data.isEmpty())
        val queryObject = querySlot.captured.queryObject
        assertTrue(containsKey(queryObject, "participants"))
        assertTrue(containsValue(queryObject, viewerWallet))
    }

    @Test
    fun `getExploreChallenges excludes pending joinable challenges`() {
        val querySlot = slot<Query>()
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        every { mongoTemplate.find(capture(querySlot), B3trChallenge::class.java) } returns
            emptyList()

        val result = service.getExploreChallenges(viewer, pageable)

        assertTrue(result.data.isEmpty())
        val queryObject = querySlot.captured.queryObject
        assertTrue(containsKey(queryObject, "startRound"))
        assertTrue(containsKey(queryObject, "participantCount"))
    }

    @Test
    fun `getExploreChallenges excludes ended challenges awaiting finalization`() {
        val querySlot = slot<Query>()
        stubUiRuntime(currentRound = 7, maxParticipants = 100)
        every { mongoTemplate.find(capture(querySlot), B3trChallenge::class.java) } returns
            emptyList()

        val result = service.getExploreChallenges(viewer, pageable)

        assertTrue(result.data.isEmpty())
        val queryObject = querySlot.captured.queryObject
        assertTrue(containsKey(queryObject, "endRound"))
    }

    @Test
    fun `getExploreChallenges paginates ui pages without overlap or overflow`() {
        stubUiRuntime(currentRound = 5, maxParticipants = 100)
        stubPagedFind(
            (30L downTo 1L).map { challengeId ->
                challenge(
                    challengeId = challengeId,
                    participantCount = 2,
                    startRound = 5,
                    visibility = ChallengeVisibility.Public,
                    creator = wallet((challengeId + 100).toString(16)),
                )
            }
        )

        val firstPage = service.getExploreChallenges(viewer, pageRequest(page = 0, size = 12))
        val secondPage = service.getExploreChallenges(viewer, pageRequest(page = 1, size = 12))
        val thirdPage = service.getExploreChallenges(viewer, pageRequest(page = 2, size = 12))

        assertEquals((30L downTo 19L).toList(), firstPage.data.map { it.challengeId })
        assertEquals((18L downTo 7L).toList(), secondPage.data.map { it.challengeId })
        assertEquals((6L downTo 1L).toList(), thirdPage.data.map { it.challengeId })
        assertTrue(firstPage.pagination.hasNext)
        assertTrue(secondPage.pagination.hasNext)
        assertFalse(thirdPage.pagination.hasNext)
    }

    @Test
    fun `getChallengeHistory returns finished challenge without pending refund action`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 1,
                    startRound = 5,
                    kind = ChallengeKind.Stake,
                    status = ChallengeStatus.Invalid,
                    participants = listOf(viewerWallet),
                    refundedBy = listOf(viewerWallet),
                )
            )

        val neededActions = service.getNeededActionChallenges(viewer, pageable)
        val history = service.getChallengeHistory(viewer, pageable)

        assertTrue(neededActions.data.isEmpty())
        assertEquals(1, history.data.size)
        assertFalse(history.data.single().canRefund)
    }

    @Test
    fun `getNeededActionChallenges respects qualified split claim actions`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100, participantActions = 6)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 2,
                    startRound = 5,
                    kind = ChallengeKind.Sponsored,
                    status = ChallengeStatus.Finalized,
                    settlementMode = SettlementMode.QualifiedSplit,
                    threshold = BigInteger.valueOf(5),
                    creator = wallet("abc"),
                    participants = listOf(viewerWallet, wallet("123")),
                    totalPrize = BigInteger.valueOf(500),
                )
            )

        val result = service.getNeededActionChallenges(viewer, pageable)

        assertEquals(1, result.data.size)
        assertTrue(result.data.single().canClaim)
    }

    @Test
    fun `claimed finalized challenge moves to history`() {
        stubUiRuntime(currentRound = 7, maxParticipants = 100)
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } returns
            listOf(
                challenge(
                    participantCount = 2,
                    startRound = 5,
                    kind = ChallengeKind.Sponsored,
                    status = ChallengeStatus.Finalized,
                    settlementMode = SettlementMode.QualifiedSplit,
                    threshold = BigInteger.valueOf(5),
                    creator = wallet("abc"),
                    participants = listOf(viewerWallet, wallet("123")),
                    claimedBy = listOf(viewerWallet),
                )
            )

        val neededActions = service.getNeededActionChallenges(viewer, pageable)
        val history = service.getChallengeHistory(viewer, pageable)

        assertTrue(neededActions.data.isEmpty())
        assertEquals(1, history.data.size)
        assertFalse(history.data.single().canClaim)
    }

    private fun currentRoundResult(roundId: Long) =
        InspectionResult(
            data = uint256Hex(roundId),
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
            reverted = false,
            vmError = null,
        )

    private fun uint256Hex(value: Long): String = "0x" + value.toString(16).padStart(64, '0')

    private fun stubUiRuntime(
        currentRound: Long,
        maxParticipants: Long,
        participantActions: Long? = null,
    ) {
        coEvery { thorClient.inspectClauses(any(), any()) } coAnswers
            {
                when (firstArg<List<Clause>>().size) {
                    2 ->
                        listOf(
                            currentRoundResult(currentRound),
                            currentRoundResult(maxParticipants),
                        )
                    1 ->
                        participantActions?.let { listOf(currentRoundResult(it)) }
                            ?: error("Unexpected participant actions lookup")
                    else -> error("Unexpected clause count")
                }
            }
    }

    private fun stubPagedFind(challenges: List<B3trChallenge>) {
        every { mongoTemplate.find(any<Query>(), B3trChallenge::class.java) } answers
            {
                val query = firstArg<Query>()
                val limit = query.limit
                challenges.drop(query.skip.toInt()).let { page ->
                    if (limit > 0) {
                        page.take(limit)
                    } else {
                        page
                    }
                }
            }
    }

    private fun containsKey(value: Any?, key: String): Boolean =
        when (value) {
            is Map<*, *> ->
                value.entries.any { entry -> entry.key == key || containsKey(entry.value, key) }
            is Iterable<*> -> value.any { containsKey(it, key) }
            else -> false
        }

    private fun containsValue(value: Any?, expected: Any): Boolean =
        when (value) {
            is Map<*, *> ->
                value.entries.any { entry ->
                    entry.key == expected || containsValue(entry.value, expected)
                }
            is Iterable<*> -> value.any { containsValue(it, expected) }
            else -> value == expected
        }

    private fun challenge(
        participantCount: Int,
        startRound: Int,
        challengeId: Long = 1L,
        endRound: Int = startRound + 1,
        kind: ChallengeKind = ChallengeKind.Stake,
        visibility: ChallengeVisibility = ChallengeVisibility.Public,
        status: ChallengeStatus = ChallengeStatus.Pending,
        settlementMode: SettlementMode = SettlementMode.None,
        creator: String = wallet("abc"),
        threshold: BigInteger = BigInteger.ZERO,
        totalPrize: BigInteger = BigInteger.TEN,
        bestScore: BigInteger = BigInteger.ZERO,
        bestCount: Int = 0,
        qualifiedCount: Int = 0,
        payoutsClaimed: Int = 0,
        participants: List<String>? = null,
        invited: List<String> = emptyList(),
        declined: List<String> = emptyList(),
        eligibleInvitees: List<String>? = null,
        claimedBy: List<String> = emptyList(),
        refundedBy: List<String> = emptyList(),
        selectedApps: List<String> = emptyList(),
        allApps: Boolean = selectedApps.isEmpty(),
    ) =
        B3trChallenge(
            version = 1,
            blockId = "0xblock",
            blockNumber = 10L,
            blockTimestamp = 1_000L,
            challengeId = challengeId,
            kind = kind,
            visibility = visibility,
            thresholdMode = ThresholdMode.None,
            status = status,
            settlementMode = settlementMode,
            creator = creator,
            title = "Spring Sprint",
            description = "",
            imageURI = "",
            metadataURI = "",
            stakeAmount = BigInteger.TEN,
            startRound = startRound,
            endRound = endRound,
            duration = endRound - startRound + 1,
            threshold = threshold,
            allApps = allApps,
            totalPrize = totalPrize,
            participantCount = participants?.size ?: participantCount,
            invitedCount = invited.size,
            declinedCount = declined.size,
            selectedAppsCount = selectedApps.size,
            bestScore = bestScore,
            bestCount = bestCount,
            qualifiedCount = qualifiedCount,
            payoutsClaimed = payoutsClaimed,
            participants =
                participants
                    ?: List(participantCount) { index -> wallet((index + 1).toString(16)) },
            invited = invited,
            declined = declined,
            selectedApps = selectedApps,
            eligibleInvitees = eligibleInvitees ?: (invited + declined).distinct(),
            claimedBy = claimedBy,
            refundedBy = refundedBy,
            createdAtBlockNumber = 10L,
            createdAtBlockTimestamp = 1_000L,
            createdTxId = "0xtx",
        )

    private fun wallet(suffix: String): String = "0x" + suffix.padStart(40, '0')
}
