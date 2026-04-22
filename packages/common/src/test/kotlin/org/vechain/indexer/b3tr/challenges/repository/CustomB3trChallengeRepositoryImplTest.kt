package org.vechain.indexer.b3tr.challenges.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.aggregation.TypedAggregation
import org.vechain.indexer.b3tr.challenges.B3trChallenge
import org.vechain.indexer.b3tr.challenges.ChallengeKind
import org.vechain.indexer.b3tr.challenges.ChallengeStatus
import org.vechain.indexer.b3tr.challenges.ChallengeType
import org.vechain.indexer.b3tr.challenges.ChallengeVisibility
import org.vechain.indexer.b3tr.challenges.SettlementMode

class CustomB3trChallengeRepositoryImplTest {
    private val mongoTemplate: MongoTemplate = mockk()
    private val repository = CustomB3trChallengeRepositoryImpl(mongoTemplate)

    @Test
    fun `findByWalletAndStatus builds aggregation and paginates via extra record`() {
        val aggregation = slot<TypedAggregation<*>>()
        every { mongoTemplate.aggregate(capture(aggregation), B3trChallenge::class.java) } returns
            AggregationResults(listOf(challenge(1L), challenge(2L), challenge(3L)), Document())

        val pageable =
            PageRequest.of(
                1,
                2,
                Sort.by(
                    Sort.Direction.DESC,
                    "challengeCreatedAtBlockTimestamp",
                    B3trChallenge::challengeId.name,
                ),
            )

        val result =
            repository.findByWalletAndStatus(
                wallet = "0x0000000000000000000000000000000000000abc",
                status = ChallengeStatus.Active,
                pageable = pageable,
            )

        val pipeline = aggregation.captured.toPipeline(Aggregation.DEFAULT_CONTEXT)

        assertEquals(2, result.content.size)
        assertEquals(true, result.hasNext())
        assertEquals(1L, result.content.first().challengeId)
        assertEquals(
            listOf(
                "Document{{\$match=Document{{wallet=0x0000000000000000000000000000000000000abc}}}}",
                "Document{{\$lookup=Document{{from=b3tr_challenges, localField=challengeId, foreignField=challengeId, as=challenge}}}}",
                "Document{{\$unwind=\$challenge}}",
                "Document{{\$match=Document{{challenge.status=Active}}}}",
                "Document{{\$sort=Document{{challengeCreatedAtBlockTimestamp=-1, challengeId=-1}}}}",
                "Document{{\$skip=2}}",
                "Document{{\$limit=3}}",
                "Document{{\$replaceRoot=Document{{newRoot=\$challenge}}}}",
            ),
            pipeline.map(Document::toString),
        )
    }

    @Test
    fun `findByWalletAndStatus omits status match when absent`() {
        val aggregation = slot<TypedAggregation<*>>()
        every { mongoTemplate.aggregate(capture(aggregation), B3trChallenge::class.java) } returns
            AggregationResults(listOf(challenge(1L)), Document())

        repository.findByWalletAndStatus(
            wallet = "0x0000000000000000000000000000000000000abc",
            status = null,
            pageable =
                PageRequest.of(
                    0,
                    2,
                    Sort.by(
                        Sort.Direction.ASC,
                        "challengeCreatedAtBlockTimestamp",
                        B3trChallenge::challengeId.name,
                    ),
                ),
        )

        val pipeline = aggregation.captured.toPipeline(Aggregation.DEFAULT_CONTEXT)

        assertEquals(
            false,
            pipeline.any {
                val match = it["\$match"] as? Map<*, *> ?: return@any false
                "challenge.status" in match.keys
            },
        )
    }

    private fun challenge(challengeId: Long) =
        B3trChallenge(
            version = 1,
            blockId = "0x1",
            blockNumber = 1L,
            blockTimestamp = 1L,
            challengeId = challengeId,
            kind = ChallengeKind.Stake,
            visibility = ChallengeVisibility.Public,
            challengeType = ChallengeType.MaxActions,
            onChainStatus = ChallengeStatus.Pending,
            status = ChallengeStatus.Active,
            settlementMode = SettlementMode.None,
            creator = "0x0000000000000000000000000000000000000abc",
            title = "Challenge $challengeId",
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
            bestScore = BigInteger.ZERO,
            bestCount = 0,
            payoutsClaimed = 0,
            participants = emptyList(),
            invited = emptyList(),
            declined = emptyList(),
            selectedApps = emptyList(),
            winners = emptyList(),
            eligibleInvitees = emptyList(),
            claimedBy = emptyList(),
            refundedBy = emptyList(),
            creatorRefunded = false,
            createdAtBlockNumber = 1L,
            createdAtBlockTimestamp = challengeId,
            createdTxId = "0xtx",
        )
}
