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

    // --- /api/v1/b3tr/challenges ---

    @Test
    fun `getPublicChallenges filters by Active status`() {
        every {
            challengeRepository.findByVisibilityAndStatus(
                ChallengeVisibility.Public,
                ChallengeStatus.Active,
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        val result = service.getPublicChallenges(ChallengeStatus.Active, pageable)

        assertEquals(1, result.data.size)
        assertEquals(ChallengeStatus.Active, result.data.single().status)
    }

    @Test
    fun `getPublicChallenges without status returns all public`() {
        every { challengeRepository.findByVisibility(ChallengeVisibility.Public, pageable) } returns
            SliceImpl(listOf(challenge()))

        val result = service.getPublicChallenges(null, pageable)

        assertEquals(1, result.data.size)
    }

    // --- /api/v1/b3tr/users/{wallet}/challenges ---

    @Test
    fun `getWalletChallenges MyChallenges delegates to findByFilter`() {
        every {
            challengeRepository.findByFilter(
                "0x0000000000000000000000000000000000000abc",
                ChallengeFilter.MyChallenges,
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        val result =
            service.getWalletChallenges(
                wallet = Address("0x0000000000000000000000000000000000000abc"),
                filter = ChallengeFilter.MyChallenges,
                pageable = pageable,
            )

        assertEquals(1, result.data.size)
    }

    @Test
    fun `getWalletChallenges History delegates to findByFilter`() {
        every {
            challengeRepository.findByFilter(
                "0x0000000000000000000000000000000000000abc",
                ChallengeFilter.History,
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        service.getWalletChallenges(
            wallet = Address("0x0000000000000000000000000000000000000abc"),
            filter = ChallengeFilter.History,
            pageable = pageable,
        )
    }

    @Test
    fun `getWalletChallenges NeededAction delegates to findByFilter`() {
        every {
            challengeRepository.findByFilter(
                "0x0000000000000000000000000000000000000abc",
                ChallengeFilter.NeededAction,
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        service.getWalletChallenges(
            wallet = Address("0x0000000000000000000000000000000000000abc"),
            filter = ChallengeFilter.NeededAction,
            pageable = pageable,
        )
    }

    @Test
    fun `getWalletChallenges OpenToJoin excludes wallet's existing challengeIds`() {
        every {
            challengeRepository.findUserChallengeIdsByWallet(
                "0x0000000000000000000000000000000000000abc"
            )
        } returns listOf(10L, 20L)
        every {
            challengeRepository.findByVisibilityAndStatusExcludingIds(
                ChallengeVisibility.Public,
                ChallengeStatus.Pending,
                listOf(10L, 20L),
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        val result =
            service.getWalletChallenges(
                wallet = Address("0x0000000000000000000000000000000000000abc"),
                filter = ChallengeFilter.OpenToJoin,
                pageable = pageable,
            )

        assertEquals(1, result.data.size)
    }

    @Test
    fun `getWalletChallenges OthersActive targets Active Public not-involved`() {
        every {
            challengeRepository.findUserChallengeIdsByWallet(
                "0x0000000000000000000000000000000000000abc"
            )
        } returns emptyList()
        every {
            challengeRepository.findByVisibilityAndStatusExcludingIds(
                ChallengeVisibility.Public,
                ChallengeStatus.Active,
                emptyList(),
                pageable,
            )
        } returns SliceImpl(listOf(challenge()))

        service.getWalletChallenges(
            wallet = Address("0x0000000000000000000000000000000000000abc"),
            filter = ChallengeFilter.OthersActive,
            pageable = pageable,
        )
    }

    // --- getChallenge(id) ---

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
            endRoundPassed = false,
            createdAtBlockNumber = 1L,
            createdAtBlockTimestamp = 1L,
            createdTxId = "0xtx",
        )
}
