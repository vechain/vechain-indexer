package org.vechain.indexer.b3tr.challenges

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.thor.Address

class ChallengesServiceTest {
    private val repository: ChallengeReadRepository = mockk()

    private val service = ChallengesService(repository)

    private val wallet = "0x0000000000000000000000000000000000000abc"

    private fun pageable(field: String) = PageRequest.of(0, 10, Sort.by(Direction.DESC, field))

    private val byCreation = pageable(B3trChallenge::createdAtBlockTimestamp.name)

    private val byWallet = pageable(B3trUserChallenge::challengeCreatedAtBlockTimestamp.name)

    @Test
    fun `public challenges are read with the status asked for`() {
        every { repository.findPublic(any(), any(), any(), any()) } returns listOf(challenge())

        val result = service.getPublicChallenges(ChallengeStatus.Active, byCreation)

        assertEquals(1, result.data.size)
        assertEquals(ChallengeStatus.Active, result.data.single().status)
        verify { repository.findPublic(ChallengeStatus.Active, 0, 11, Direction.DESC) }

        service.getPublicChallenges(null, byCreation)
        verify { repository.findPublic(null, 0, 11, Direction.DESC) }
    }

    @Test
    fun `a wallet's buckets are read from its own rows, sorted by the challenge it holds`() {
        every { repository.findByFilter(any(), any(), any(), any(), any()) } returns
            listOf(challenge())

        listOf(
                ChallengeFilter.MyChallenges,
                ChallengeFilter.History,
                ChallengeFilter.NeededAction,
            )
            .forEach { filter ->
                val result = service.getWalletChallenges(Address(wallet), filter, byWallet)

                assertEquals(1, result.data.size)
                verify { repository.findByFilter(wallet, filter, 0, 11, Direction.DESC) }
            }
    }

    @Test
    fun `the open buckets are the public challenges the wallet is not in`() {
        every { repository.findOpenTo(any(), any(), any(), any(), any()) } returns
            listOf(challenge())

        service.getWalletChallenges(Address(wallet), ChallengeFilter.OpenToJoin, byCreation)
        verify { repository.findOpenTo(wallet, ChallengeStatus.Pending, 0, 11, Direction.DESC) }

        service.getWalletChallenges(Address(wallet), ChallengeFilter.OthersActive, byCreation)
        verify { repository.findOpenTo(wallet, ChallengeStatus.Active, 0, 11, Direction.DESC) }
    }

    @Test
    fun `a challenge reads back with its detail facts, and an unknown id is a 404`() {
        every { repository.findById(1L) } returns challenge()
        every { repository.findById(99L) } returns null

        val result = service.getChallenge(1L)

        assertEquals("7", result.bestScore)
        assertEquals(2, result.bestCount)
        assertEquals(1, result.payoutsClaimed)
        assertEquals(listOf("0x0000000000000000000000000000000000000def"), result.eligibleInvitees)
        assertEquals(listOf("0x0000000000000000000000000000000000000abc"), result.claimedBy)
        assertEquals(listOf("0x0000000000000000000000000000000000000def"), result.refundedBy)
        assertEquals(true, result.creatorRefunded)

        assertThrows(ResourceNotFoundException::class.java) { service.getChallenge(99L) }
    }

    @Test
    fun `the indexed heads come from the two tables`() {
        every { repository.latestBlockNumber() } returns 42L
        every { repository.latestUserBlockNumber() } returns 41L

        assertEquals(
            mapOf("B3trChallenges" to 42L, "B3trUserChallenges" to 41L),
            service.getLatestIndexedBlocks(),
        )
    }

    private fun challenge(visibility: ChallengeVisibility = ChallengeVisibility.Public) =
        B3trChallenge(
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
