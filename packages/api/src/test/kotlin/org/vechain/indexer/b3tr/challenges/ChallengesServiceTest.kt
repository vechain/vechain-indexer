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
    fun `getChallenges filters by Active status`() {
        every {
            challengeRepository.findByVisibilityAndStatus(
                ChallengeVisibility.Public,
                ChallengeStatus.Active,
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        val result = service.getChallenges(ChallengeStatus.Active, null, pageable)

        assertEquals(1, result.data.size)
        assertEquals(ChallengeStatus.Active, result.data.single().status)
    }

    @Test
    fun `getChallenges filters by Pending status`() {
        every {
            challengeRepository.findByVisibilityAndStatus(
                ChallengeVisibility.Public,
                ChallengeStatus.Pending,
                pageable,
            )
        } returns SliceImpl(emptyList())

        val result = service.getChallenges(ChallengeStatus.Pending, null, pageable)

        assertEquals(0, result.data.size)
    }

    @Test
    fun `getChallenges filters by Completed status`() {
        every {
            challengeRepository.findByVisibilityAndStatus(
                ChallengeVisibility.Public,
                ChallengeStatus.Completed,
                pageable,
            )
        } returns SliceImpl(emptyList())

        val result = service.getChallenges(ChallengeStatus.Completed, null, pageable)

        assertEquals(0, result.data.size)
    }

    @Test
    fun `getChallenges without status returns all public challenges`() {
        every { challengeRepository.findByVisibility(ChallengeVisibility.Public, pageable) } returns
            SliceImpl(listOf(challenge()))

        val result = service.getChallenges(null, null, pageable)

        assertEquals(1, result.data.size)
    }

    @Test
    fun `getChallenges with wallet queries hydrated challenges`() {
        every {
            challengeRepository.findByWalletAndStatus(
                "0x0000000000000000000000000000000000000abc",
                null,
                pageable,
            )
        } returns SliceImpl(listOf(challenge(visibility = ChallengeVisibility.Private)))

        val result =
            service.getChallenges(
                status = null,
                wallet = Address("0x0000000000000000000000000000000000000abc"),
                pageable = pageable,
            )

        assertEquals(1, result.data.size)
        assertEquals(1L, result.data.single().challengeId)
        assertEquals(ChallengeVisibility.Private, result.data.single().visibility)
    }

    @Test
    fun `getChallenges with wallet and status filters hydrated challenges`() {
        every {
            challengeRepository.findByWalletAndStatus(
                "0x0000000000000000000000000000000000000abc",
                ChallengeStatus.Active,
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        val result =
            service.getChallenges(
                status = ChallengeStatus.Active,
                wallet = Address("0x0000000000000000000000000000000000000abc"),
                pageable = pageable,
            )

        assertEquals(1, result.data.size)
        assertEquals(ChallengeStatus.Active, result.data.single().status)
    }

    private fun challenge(visibility: ChallengeVisibility = ChallengeVisibility.Public) =
        B3trChallenge(
            version = 1,
            blockId = "0x1",
            blockNumber = 1L,
            blockTimestamp = 1L,
            challengeId = 1L,
            kind = ChallengeKind.Stake,
            visibility = visibility,
            challengeType = ChallengeType.MaxActions,
            onChainStatus = ChallengeStatus.Pending,
            status = ChallengeStatus.Active,
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
}
