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
import org.vechain.indexer.b3tr.challenges.B3trUserChallenge
import org.vechain.indexer.b3tr.challenges.ChallengeFilter
import org.vechain.indexer.b3tr.challenges.ChallengeKind
import org.vechain.indexer.b3tr.challenges.ChallengeStatus
import org.vechain.indexer.b3tr.challenges.ChallengeType
import org.vechain.indexer.b3tr.challenges.ChallengeVisibility
import org.vechain.indexer.b3tr.challenges.ParticipantStatus
import org.vechain.indexer.b3tr.challenges.SettlementMode

class CustomB3trChallengeRepositoryImplTest {
    private val mongoTemplate: MongoTemplate = mockk()
    private val repository = CustomB3trChallengeRepositoryImpl(mongoTemplate)

    @Test
    fun `findByFilter MyChallenges matches creator-or-joined and in-progress status`() {
        val aggregation = slot<TypedAggregation<*>>()
        every { mongoTemplate.aggregate(capture(aggregation), B3trChallenge::class.java) } returns
            AggregationResults(listOf(challenge(1L)), Document())

        repository.findByFilter(
            wallet = "0x0000000000000000000000000000000000000abc",
            filter = ChallengeFilter.MyChallenges,
            pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "challengeId")),
        )

        val pipeline = aggregation.captured.toPipeline(Aggregation.DEFAULT_CONTEXT)
        val firstMatch = pipeline.first()["\$match"] as Map<*, *>
        val firstMatchSerialized = firstMatch.toString()
        assertEquals(
            true,
            firstMatchSerialized.contains("0x0000000000000000000000000000000000000abc"),
        )
        assertEquals(true, firstMatchSerialized.contains("isCreator"))
        assertEquals(true, firstMatchSerialized.contains("participantStatus"))
        val statusMatches =
            pipeline.filter {
                val m = it["\$match"] as? Map<*, *> ?: return@filter false
                "challenge.status" in m.keys
            }
        assertEquals(1, statusMatches.size)
        val allowed =
            ((statusMatches.single()["\$match"] as Map<*, *>)["challenge.status"] as Map<*, *>)[
                "\$in"]
                as List<*>
        assertEquals(setOf(ChallengeStatus.Pending, ChallengeStatus.Active), allowed.toSet())
    }

    @Test
    fun `findByFilter History matches wallet and terminal statuses`() {
        val aggregation = slot<TypedAggregation<*>>()
        every { mongoTemplate.aggregate(capture(aggregation), B3trChallenge::class.java) } returns
            AggregationResults(emptyList(), Document())

        repository.findByFilter(
            wallet = "0x0000000000000000000000000000000000000abc",
            filter = ChallengeFilter.History,
            pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "challengeId")),
        )

        val pipeline = aggregation.captured.toPipeline(Aggregation.DEFAULT_CONTEXT)
        val firstMatch = pipeline.first()["\$match"] as Map<*, *>
        assertEquals("0x0000000000000000000000000000000000000abc", firstMatch["wallet"])
        val statusMatch =
            pipeline
                .first { (it["\$match"] as? Map<*, *>)?.containsKey("challenge.status") == true }[
                    "\$match"]
                as Map<*, *>
        val allowed = (statusMatch["challenge.status"] as Map<*, *>)["\$in"] as List<*>
        assertEquals(
            setOf(ChallengeStatus.Completed, ChallengeStatus.Cancelled, ChallengeStatus.Invalid),
            allowed.toSet(),
        )
    }

    @Test
    fun `findByFilter NeededAction builds an OR across sub-buckets`() {
        val aggregation = slot<TypedAggregation<*>>()
        every { mongoTemplate.aggregate(capture(aggregation), B3trChallenge::class.java) } returns
            AggregationResults(emptyList(), Document())

        repository.findByFilter(
            wallet = "0x0000000000000000000000000000000000000abc",
            filter = ChallengeFilter.NeededAction,
            pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "challengeId")),
        )

        val pipeline = aggregation.captured.toPipeline(Aggregation.DEFAULT_CONTEXT)
        val matches = pipeline.mapNotNull { it["\$match"] as? Map<*, *> }
        val orMatch = matches.single { it.containsKey("\$or") }
        val branches = orMatch["\$or"] as List<*>
        assertEquals(true, branches.size >= 4)
    }

    @Test
    fun `findByFilter OpenToJoin throws — served by findByVisibilityAndStatusExcludingIds`() {
        try {
            repository.findByFilter(
                wallet = "0x0000000000000000000000000000000000000abc",
                filter = ChallengeFilter.OpenToJoin,
                pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "challengeId")),
            )
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `findByVisibilityAndStatusExcludingIds adds nin when ids provided`() {
        val query = slot<org.springframework.data.mongodb.core.query.Query>()
        every { mongoTemplate.find(capture(query), B3trChallenge::class.java) } returns
            listOf(challenge(1L))

        repository.findByVisibilityAndStatusExcludingIds(
            visibility = ChallengeVisibility.Public,
            status = ChallengeStatus.Pending,
            excludeChallengeIds = listOf(7L, 8L),
            pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAtBlockTimestamp")),
        )

        val queryDoc = query.captured.queryObject
        assertEquals(true, queryDoc.containsKey("\$and"))
        val andClauses = queryDoc["\$and"] as List<*>
        val serialized = andClauses.joinToString(separator = " ") { it.toString() }
        assertEquals(true, serialized.contains("challengeId"))
        assertEquals(true, serialized.contains("\$nin"))
    }

    @Test
    fun `findByVisibilityAndStatusExcludingIds skips nin when ids empty`() {
        val query = slot<org.springframework.data.mongodb.core.query.Query>()
        every { mongoTemplate.find(capture(query), B3trChallenge::class.java) } returns emptyList()

        repository.findByVisibilityAndStatusExcludingIds(
            visibility = ChallengeVisibility.Public,
            status = ChallengeStatus.Active,
            excludeChallengeIds = emptyList(),
            pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "challengeId")),
        )

        val queryDoc = query.captured.queryObject
        assertEquals(false, queryDoc.toString().contains("\$nin"))
    }

    @Test
    fun `findUserChallengeIdsByWallet filters out phantom None non-creator records`() {
        val query = slot<org.springframework.data.mongodb.core.query.Query>()
        every {
            mongoTemplate.findDistinct(
                capture(query),
                B3trUserChallenge::challengeId.name,
                B3trUserChallenge::class.java,
                Long::class.javaObjectType,
            )
        } returns listOf(1L, 2L)

        repository.findUserChallengeIdsByWallet("0x0000000000000000000000000000000000000abc")

        val queryDoc = query.captured.queryObject
        assertEquals(true, queryDoc.containsKey("\$and"))
        val andClauses = queryDoc["\$and"] as List<*>
        val serialized = andClauses.joinToString(separator = " ") { it.toString() }
        assertEquals(true, serialized.contains("0x0000000000000000000000000000000000000abc"))
        assertEquals(true, serialized.contains("isCreator"))
        assertEquals(true, serialized.contains("participantStatus"))
        assertEquals(true, serialized.contains("\$ne"))
        assertEquals(true, serialized.contains(ParticipantStatus.None.toString()))
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
            endRoundPassed = false,
            createdAtBlockNumber = 1L,
            createdAtBlockTimestamp = challengeId,
            createdTxId = "0xtx",
        )
}
