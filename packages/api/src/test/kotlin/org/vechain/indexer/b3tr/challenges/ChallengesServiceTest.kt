package org.vechain.indexer.b3tr.challenges

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.b3tr.challenges.repository.B3trUserChallengeRepository
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.thor.Address

class ChallengesServiceTest {
    private val challengeRepository: B3trChallengeRepository = mockk()
    private val userChallengeRepository: B3trUserChallengeRepository = mockk()

    private val service =
        ChallengesService(
            challengeRepository = challengeRepository,
            userChallengeRepository = userChallengeRepository,
        )

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

    @Test
    fun `getChallenges queries public challenges by phase`() {
        every {
            challengeRepository.findByVisibilityAndPhase(
                ChallengeVisibility.Public,
                ChallengePhase.Live,
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        val result = service.getChallenges(ChallengePhase.Live, pageable)

        assertEquals(1, result.data.size)
        assertEquals(ChallengePhase.Live, result.data.single().phase)
    }

    @Test
    fun `getUserChallenges queries wallet list without stitching`() {
        every {
            userChallengeRepository.findByWallet(
                "0x0000000000000000000000000000000000000abc",
                pageable,
            )
        } returns SliceImpl(listOf(userChallenge()))

        val result =
            service.getUserChallenges(
                wallet = Address("0x0000000000000000000000000000000000000abc"),
                pageable = pageable,
            )

        assertEquals(1, result.data.size)
        assertEquals(1L, result.data.single().challengeId)
        assertEquals(1L, result.data.single().createdAt)
    }

    private fun challenge() =
        B3trChallenge(
            version = 1,
            blockId = "0x1",
            blockNumber = 1L,
            blockTimestamp = 1L,
            challengeId = 1L,
            kind = ChallengeKind.Stake,
            visibility = ChallengeVisibility.Public,
            challengeType = ChallengeType.MaxActions,
            status = ChallengeStatus.Pending,
            lifecycleStatus = ChallengeStatus.Active,
            phase = ChallengePhase.Live,
            settlementMode = SettlementMode.None,
            creator = "0x0000000000000000000000000000000000000abc",
            title = "Challenge",
            description = "desc",
            imageURI = "ipfs://image",
            metadataURI = "ipfs://meta",
            stakeAmount = BigInteger.TEN,
            startRound = 1,
            endRound = 2,
            duration = 2,
            threshold = BigInteger.ZERO,
            numWinners = 0,
            winnersClaimed = 0,
            prizePerWinner = BigInteger.ZERO,
            allApps = true,
            totalPrize = BigInteger.TEN,
            participantCount = 1,
            invitedCount = 0,
            declinedCount = 0,
            selectedAppsCount = 0,
            winnersCount = 0,
            bestScore = BigInteger.valueOf(7),
            bestCount = 2,
            payoutsClaimed = 1,
            participants = listOf("0x0000000000000000000000000000000000000abc"),
            invited = emptyList(),
            declined = emptyList(),
            selectedApps = emptyList(),
            winners = emptyList(),
            eligibleInvitees = listOf("0x0000000000000000000000000000000000000def"),
            claimedBy = listOf("0x0000000000000000000000000000000000000abc"),
            refundedBy = listOf("0x0000000000000000000000000000000000000def"),
            creatorRefunded = true,
            createdAtBlockNumber = 1L,
            createdAtBlockTimestamp = 1L,
            createdTxId = "0xtx",
            currentRound = 1,
            maxParticipants = 100,
        )

    @Test
    fun `getChallenge throws when challenge does not exist`() {
        every { challengeRepository.findById(B3trChallenge.documentId(99L)) } returns
            java.util.Optional.empty()

        assertThrows(ResourceNotFoundException::class.java) { service.getChallenge(99L) }
    }

    @Test
    fun `getChallenge exposes raw challenge detail facts`() {
        every { challengeRepository.findById(B3trChallenge.documentId(1L)) } returns
            java.util.Optional.of(challenge())

        val result = service.getChallenge(1L)

        assertEquals("7", result.bestScore)
        assertEquals(2, result.bestCount)
        assertEquals(1, result.payoutsClaimed)
        assertEquals(listOf("0x0000000000000000000000000000000000000def"), result.eligibleInvitees)
        assertEquals(listOf("0x0000000000000000000000000000000000000abc"), result.claimedBy)
        assertEquals(listOf("0x0000000000000000000000000000000000000def"), result.refundedBy)
        assertEquals(true, result.creatorRefunded)
    }

    private fun userChallenge() =
        B3trUserChallenge(
            version = 1,
            blockId = "0x1",
            blockNumber = 1L,
            blockTimestamp = 1L,
            wallet = "0x0000000000000000000000000000000000000abc",
            challengeId = 1L,
            challengeCreatedAtBlockTimestamp = 1L,
        )
}
