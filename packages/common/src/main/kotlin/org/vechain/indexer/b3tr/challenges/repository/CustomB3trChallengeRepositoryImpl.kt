package org.vechain.indexer.b3tr.challenges.repository

import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.challenges.B3trChallenge
import org.vechain.indexer.b3tr.challenges.B3trUserChallenge
import org.vechain.indexer.b3tr.challenges.ChallengeFilter
import org.vechain.indexer.b3tr.challenges.ChallengeKind
import org.vechain.indexer.b3tr.challenges.ChallengeStatus
import org.vechain.indexer.b3tr.challenges.ChallengeType
import org.vechain.indexer.b3tr.challenges.ChallengeVisibility
import org.vechain.indexer.b3tr.challenges.ParticipantStatus
import org.vechain.indexer.utils.SliceBuilder

@Profile("b3tr", "b3tr-challenges")
@Repository
open class CustomB3trChallengeRepositoryImpl
@Autowired
constructor(private val mongoTemplate: MongoTemplate) : CustomB3trChallengeRepository {

    override fun findByFilter(
        wallet: String,
        filter: ChallengeFilter,
        pageable: Pageable,
    ): Slice<B3trChallenge> {
        val operations = buildList {
            add(match(walletAndFilterCriteria(wallet, filter)))
            addAll(challengeLookupOperations())
            add(match(challengeStatusCriteriaForFilter(filter)))
            addAll(paginationOperations(pageable))
        }
        return runAggregation(operations, pageable)
    }

    override fun findByVisibilityAndStatusExcludingIds(
        visibility: ChallengeVisibility,
        status: ChallengeStatus,
        excludeChallengeIds: Collection<Long>,
        pageable: Pageable,
    ): Slice<B3trChallenge> {
        val baseCriteria =
            listOf(
                Criteria.where(B3trChallenge::visibility.name).`is`(visibility),
                Criteria.where(B3trChallenge::status.name).`is`(status),
            )
        val exclusion =
            if (excludeChallengeIds.isEmpty()) emptyList()
            else listOf(Criteria.where(B3trChallenge::challengeId.name).nin(excludeChallengeIds))
        val criteria = Criteria().andOperator(*(baseCriteria + exclusion).toTypedArray())

        val query =
            org.springframework.data.mongodb.core.query
                .Query(criteria)
                .with(pageable.sort)
                .skip(pageable.offset)
                .limit(pageable.pageSize + 1)

        val results = mongoTemplate.find(query, B3trChallenge::class.java)
        return SliceBuilder.buildResultsSlice(results, pageable)
    }

    override fun findUserChallengeIdsByWallet(wallet: String): List<Long> {
        // Only consider the wallet "involved" when it is the creator or currently holds a
        // non-None participant status. A `None + !isCreator` record can survive a past
        // invite-then-leave (or any contract interaction that later cleared the on-chain
        // relationship), and if treated as "involved" would wrongly exclude the challenge
        // from OpenToJoin / OthersActive buckets.
        val criteria =
            Criteria()
                .andOperator(
                    Criteria.where(B3trUserChallenge::wallet.name).`is`(wallet),
                    Criteria()
                        .orOperator(
                            Criteria.where(B3trUserChallenge::isCreator.name).`is`(true),
                            Criteria.where(B3trUserChallenge::participantStatus.name)
                                .ne(ParticipantStatus.None),
                        ),
                )
        val query = org.springframework.data.mongodb.core.query.Query(criteria)
        return mongoTemplate.findDistinct(
            query,
            B3trUserChallenge::challengeId.name,
            B3trUserChallenge::class.java,
            Long::class.javaObjectType,
        )
    }

    private fun walletAndFilterCriteria(wallet: String, filter: ChallengeFilter): Criteria {
        val walletMatch = Criteria.where(B3trUserChallenge::wallet.name).`is`(wallet)
        return when (filter) {
            ChallengeFilter.MyChallenges ->
                Criteria()
                    .andOperator(
                        walletMatch,
                        Criteria()
                            .orOperator(
                                Criteria.where(B3trUserChallenge::isCreator.name).`is`(true),
                                Criteria.where(B3trUserChallenge::participantStatus.name)
                                    .`is`(ParticipantStatus.Joined),
                            ),
                    )
            ChallengeFilter.History,
            ChallengeFilter.NeededAction -> walletMatch
            // OpenToJoin and OthersActive are served by findByVisibilityAndStatusExcludingIds.
            ChallengeFilter.OpenToJoin,
            ChallengeFilter.OthersActive ->
                throw IllegalArgumentException("Filter $filter is not served by findByFilter")
        }
    }

    private fun challengeStatusCriteriaForFilter(filter: ChallengeFilter): Criteria {
        val statusField = "challenge.${B3trChallenge::status.name}"
        return when (filter) {
            ChallengeFilter.MyChallenges ->
                Criteria.where(statusField)
                    .`in`(listOf(ChallengeStatus.Pending, ChallengeStatus.Active))
            ChallengeFilter.History ->
                Criteria.where(statusField)
                    .`in`(
                        listOf(
                            ChallengeStatus.Completed,
                            ChallengeStatus.Cancelled,
                            ChallengeStatus.Invalid,
                        )
                    )
            ChallengeFilter.NeededAction -> neededActionCriteria()
            ChallengeFilter.OpenToJoin,
            ChallengeFilter.OthersActive ->
                throw IllegalArgumentException("Filter $filter is not served by findByFilter")
        }
    }

    private fun neededActionCriteria(): Criteria {
        val statusField = "challenge.${B3trChallenge::status.name}"
        val typeField = "challenge.${B3trChallenge::challengeType.name}"
        val kindField = "challenge.${B3trChallenge::kind.name}"
        val endRoundPassedField = "challenge.${B3trChallenge::endRoundPassed.name}"

        val invitedNotResponded =
            Criteria()
                .andOperator(
                    Criteria.where(B3trUserChallenge::participantStatus.name)
                        .`is`(ParticipantStatus.Invited),
                    Criteria.where(statusField)
                        .`in`(ChallengeStatus.Pending, ChallengeStatus.Active),
                )
        val canClaimPrize =
            Criteria()
                .andOperator(
                    Criteria.where(B3trUserChallenge::isWinner.name).`is`(true),
                    Criteria.where(B3trUserChallenge::hasClaimedPrize.name).`is`(false),
                    Criteria.where(statusField).`is`(ChallengeStatus.Completed),
                    Criteria.where(typeField).`is`(ChallengeType.MaxActions),
                )
        val canFinalize =
            Criteria()
                .andOperator(
                    Criteria.where(typeField).`is`(ChallengeType.MaxActions),
                    Criteria.where(statusField).`is`(ChallengeStatus.Active),
                    Criteria.where(endRoundPassedField).`is`(true),
                    Criteria()
                        .orOperator(
                            Criteria.where(B3trUserChallenge::participantStatus.name)
                                .`is`(ParticipantStatus.Joined),
                            Criteria.where(B3trUserChallenge::isCreator.name).`is`(true),
                        ),
                )
        // Refund eligibility mirrors the on-chain contract:
        //   - Stake     : only Joined participants can reclaim their stake.
        //   - Sponsored : only the creator can reclaim the unused sponsorship pool.
        // Applying a single Joined-OR-creator predicate would wrongly flag Joined
        // participants of Sponsored Cancelled/Invalid challenges (they never staked
        // anything, so they have no refund to claim).
        val canRefundStakeParticipant =
            Criteria()
                .andOperator(
                    Criteria.where(B3trUserChallenge::hasClaimedRefund.name).`is`(false),
                    Criteria.where(statusField)
                        .`in`(ChallengeStatus.Cancelled, ChallengeStatus.Invalid),
                    Criteria.where(kindField).`is`(ChallengeKind.Stake),
                    Criteria.where(B3trUserChallenge::participantStatus.name)
                        .`is`(ParticipantStatus.Joined),
                )
        val canRefundSponsoredCreator =
            Criteria()
                .andOperator(
                    Criteria.where(B3trUserChallenge::hasClaimedRefund.name).`is`(false),
                    Criteria.where(statusField)
                        .`in`(ChallengeStatus.Cancelled, ChallengeStatus.Invalid),
                    Criteria.where(kindField).`is`(ChallengeKind.Sponsored),
                    Criteria.where(B3trUserChallenge::isCreator.name).`is`(true),
                )
        // Mirrors the on-chain `claimCreatorSplitWinRefund` precondition: the creator can only
        // refund when there are still unclaimed slots (`winnersClaimed < numWinners`). Without
        // this, fully-settled Split Win challenges would surface as actionable for the creator
        // even though calling the contract would revert with `NothingToRefund`.
        val winnersClaimedField = "challenge.${B3trChallenge::winnersClaimed.name}"
        val numWinnersField = "challenge.${B3trChallenge::numWinners.name}"
        val hasUnclaimedSplitWinSlots =
            Criteria.where("\$expr")
                .`is`(Document("\$lt", listOf("\$$winnersClaimedField", "\$$numWinnersField")))
        val splitWinCreatorRefund =
            Criteria()
                .andOperator(
                    Criteria.where(B3trUserChallenge::isCreator.name).`is`(true),
                    Criteria.where(typeField).`is`(ChallengeType.SplitWin),
                    Criteria.where(statusField)
                        .`in`(ChallengeStatus.Active, ChallengeStatus.Completed),
                    Criteria.where(endRoundPassedField).`is`(true),
                    Criteria.where(B3trUserChallenge::hasClaimedRefund.name).`is`(false),
                    hasUnclaimedSplitWinSlots,
                )

        return Criteria()
            .orOperator(
                invitedNotResponded,
                canClaimPrize,
                canFinalize,
                canRefundStakeParticipant,
                canRefundSponsoredCreator,
                splitWinCreatorRefund,
            )
    }

    private fun challengeLookupOperations(): List<AggregationOperation> =
        listOf(
            Aggregation.lookup(
                IndexerNames.B3TR_CHALLENGES.COLLECTION,
                B3trUserChallenge::challengeId.name,
                B3trChallenge::challengeId.name,
                "challenge",
            ),
            Aggregation.unwind("challenge"),
        )

    private fun paginationOperations(pageable: Pageable): List<AggregationOperation> =
        listOf(
            Aggregation.sort(pageable.sort),
            Aggregation.skip(pageable.offset),
            Aggregation.limit(pageable.pageSize.toLong() + 1),
            Aggregation.replaceRoot("challenge"),
        )

    private fun runAggregation(
        operations: List<AggregationOperation>,
        pageable: Pageable,
    ): Slice<B3trChallenge> {
        val aggregation = Aggregation.newAggregation(B3trUserChallenge::class.java, operations)
        val results = mongoTemplate.aggregate(aggregation, B3trChallenge::class.java).mappedResults
        return SliceBuilder.buildResultsSlice(results, pageable)
    }
}
