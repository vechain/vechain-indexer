package org.vechain.indexer.b3tr.challenges

import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address as AbiAddress
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256

@Profile("b3tr", "b3tr-challenges")
@Service
open class ChallengesService(
    private val repository: B3trChallengeRepository,
    private val mongoTemplate: MongoTemplate,
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.X_ALLOC_VOTING_CONTRACT}")
    private val xAllocVotingContract: String,
    @param:Value("\${business-event.substitutions.CHALLENGES_CONTRACT}")
    private val challengesContract: String,
) : IndexerService {
    private val currentRoundFunction = Function("currentRoundId", emptyList(), emptyList())
    private val maxParticipantsFunction = Function("maxParticipants", emptyList(), emptyList())

    open fun getChallenges(
        status: ChallengeStatus?,
        kind: ChallengeKind?,
        visibility: ChallengeVisibility?,
        creator: Address?,
        participant: Address?,
        invitee: Address?,
        appId: String?,
        startRound: Int?,
        endRound: Int?,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeResponse> {
        val currentRound = getCurrentRound()

        return findChallengePage(
            filters =
                ChallengeFilters(
                    status = status,
                    kind = kind,
                    visibility = visibility,
                    creator = creator?.value,
                    participant = participant?.value,
                    invitee = invitee?.value,
                    appId = appId,
                    startRound = startRound,
                    endRound = endRound,
                ),
            currentRound = currentRound,
            pageable = pageable,
        )
    }

    open fun getNeededActionChallenges(
        wallet: Address,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        getUiChallenges(ChallengeUiSection.NeededActions, wallet, pageable)

    open fun getActiveChallenges(
        wallet: Address,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        getUiChallenges(ChallengeUiSection.Active, wallet, pageable)

    open fun getOpenChallenges(
        wallet: Address,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        getUiChallenges(ChallengeUiSection.Open, wallet, pageable)

    open fun getExploreChallenges(
        wallet: Address,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        getUiChallenges(ChallengeUiSection.Explore, wallet, pageable)

    open fun getChallengeHistory(
        wallet: Address,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        getUiChallenges(ChallengeUiSection.History, wallet, pageable)

    open fun getChallenge(challengeId: Long, wallet: Address? = null): B3trChallengeDetailResponse {
        val challenge =
            repository.findByIdOrNull(B3trChallenge.documentId(challengeId))
                ?: throw ResourceNotFoundException("Challenge not found for id $challengeId")

        val runtimeContext = getChallengeRuntimeContext()
        return buildChallengeDetailResponse(
            challenge = challenge,
            wallet = normalizeWallet(wallet),
            runtimeContext = runtimeContext,
        )
    }

    open fun computeEffectiveStatus(challenge: B3trChallenge, currentRound: Int): ChallengeStatus {
        if (challenge.status != ChallengeStatus.Pending) {
            return challenge.status
        }

        if (currentRound < challenge.startRound) {
            return ChallengeStatus.Pending
        }

        val minimumParticipants = if (challenge.kind == ChallengeKind.Stake) 2 else 1
        return if (challenge.participantCount >= minimumParticipants) {
            ChallengeStatus.Active
        } else {
            ChallengeStatus.Invalid
        }
    }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("B3trChallenges" to (repository.getLatestRecord()?.blockNumber ?: 0))

    private fun getUiChallenges(
        section: ChallengeUiSection,
        wallet: Address,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> {
        val runtimeContext = getChallengeRuntimeContext()
        val normalizedWallet = normalizeWallet(wallet)

        return when (section) {
            ChallengeUiSection.NeededActions,
            ChallengeUiSection.History ->
                findHybridUiPage(section, normalizedWallet, runtimeContext, pageable)
            else ->
                findQueryUiPage(
                    query = buildUiSectionQuery(section, normalizedWallet, runtimeContext),
                    wallet = normalizedWallet,
                    runtimeContext = runtimeContext,
                    pageable = pageable,
                )
        }
    }

    private fun buildChallengeDetailResponse(
        challenge: B3trChallenge,
        wallet: String,
        runtimeContext: ChallengeRuntimeContext,
    ): B3trChallengeDetailResponse {
        val viewerContext = buildViewerContext(challenge, wallet, runtimeContext.currentRound)
        val participantActions =
            getParticipantActions(
                candidates = listOf(challenge),
                viewerContexts = mapOf(challenge.challengeId to viewerContext),
                wallet = wallet,
            )[challenge.challengeId] ?: BigInteger.ZERO

        return B3trChallengeDetailResponse.from(
            challenge = challenge,
            state =
                buildUiState(
                    challenge = challenge,
                    runtimeContext = runtimeContext,
                    viewerContext = viewerContext,
                    participantActions = participantActions,
                ),
        )
    }

    private fun findChallengePage(
        filters: ChallengeFilters,
        currentRound: Int,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeResponse> =
        findPage(query = buildChallengeQuery(filters, currentRound), pageable = pageable) {
            B3trChallengeResponse.from(it, computeEffectiveStatus(it, currentRound))
        }

    private fun findQueryUiPage(
        query: Query,
        wallet: String,
        runtimeContext: ChallengeRuntimeContext,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        findPage(query, pageable) { buildUiResponse(it, wallet, runtimeContext) }

    private fun findHybridUiPage(
        section: ChallengeUiSection,
        wallet: String,
        runtimeContext: ChallengeRuntimeContext,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeUiResponse> {
        val pageSize = pageable.pageSize
        val requiredOffset = pageable.offset
        val chunkSize = maxOf(pageSize * 2, 100)
        val collected = mutableListOf<B3trChallengeUiResponse>()

        var skippedMatches = 0L
        var skipCandidates = 0L
        var hasMoreCandidates = true

        while (hasMoreCandidates && collected.size <= pageSize) {
            val query = buildUiSectionQuery(section, wallet, runtimeContext)
            query.with(pageable.sort)
            query.skip(skipCandidates)
            query.limit(chunkSize)

            val candidates = mongoTemplate.find(query, B3trChallenge::class.java)
            hasMoreCandidates = candidates.size == chunkSize
            skipCandidates += candidates.size.toLong()

            val viewerContexts =
                candidates.associateBy(
                    keySelector = B3trChallenge::challengeId,
                    valueTransform = { buildViewerContext(it, wallet, runtimeContext.currentRound) },
                )
            val participantActions =
                getParticipantActions(
                    candidates = candidates,
                    viewerContexts = viewerContexts,
                    wallet = wallet,
                )

            for (candidate in candidates) {
                val viewerContext = viewerContexts.getValue(candidate.challengeId)
                val uiState =
                    buildUiState(
                        challenge = candidate,
                        runtimeContext = runtimeContext,
                        viewerContext = viewerContext,
                        participantActions =
                            participantActions[candidate.challengeId] ?: BigInteger.ZERO,
                    )

                if (!belongsToSection(section, uiState)) {
                    continue
                }

                if (skippedMatches < requiredOffset) {
                    skippedMatches++
                    continue
                }

                collected.add(B3trChallengeUiResponse.from(candidate, uiState))
                if (collected.size > pageSize) {
                    break
                }
            }
        }

        val hasNext = collected.size > pageSize
        return paginatedResponse(
            data = if (hasNext) collected.dropLast(1) else collected,
            hasNext = hasNext,
            cursor = null,
        )
    }

    private fun <T : Any> findPage(
        query: Query,
        pageable: Pageable,
        mapper: (B3trChallenge) -> T,
    ): PaginatedResponse<T> {
        query.with(pageable).limit(pageable.pageSize + 1)

        val results = mongoTemplate.find(query, B3trChallenge::class.java)
        val hasNext = results.size > pageable.pageSize
        val page = if (hasNext) results.dropLast(1) else results
        val slice = SliceImpl(page.map(mapper), pageable, hasNext)
        return paginatedResponse(slice)
    }

    private fun buildBaseQuery(filters: ChallengeFilters): Query {
        val criteria = mutableListOf<Criteria>()

        filters.status?.let { criteria.add(Criteria.where(B3trChallenge::status.name).`is`(it)) }
        filters.kind?.let { criteria.add(Criteria.where(B3trChallenge::kind.name).`is`(it)) }
        filters.visibility?.let {
            criteria.add(Criteria.where(B3trChallenge::visibility.name).`is`(it))
        }
        filters.creator?.let {
            criteria.add(Criteria.where(B3trChallenge::creator.name).`is`(HexUtils.normalise(it)))
        }
        filters.participant?.let {
            criteria.add(
                Criteria.where(B3trChallenge::participants.name).`is`(HexUtils.normalise(it))
            )
        }
        filters.invitee?.let {
            criteria.add(Criteria.where(B3trChallenge::invited.name).`is`(HexUtils.normalise(it)))
        }
        filters.appId?.let {
            criteria.add(
                Criteria()
                    .orOperator(
                        Criteria.where(B3trChallenge::allApps.name).`is`(true),
                        Criteria.where(B3trChallenge::selectedApps.name).`is`(it),
                    )
            )
        }
        filters.startRound?.let {
            criteria.add(Criteria.where(B3trChallenge::startRound.name).`is`(it))
        }
        filters.endRound?.let {
            criteria.add(Criteria.where(B3trChallenge::endRound.name).`is`(it))
        }

        return if (criteria.isEmpty()) {
            Query()
        } else {
            Query(Criteria().andOperator(*criteria.toTypedArray()))
        }
    }

    private fun buildChallengeQuery(filters: ChallengeFilters, currentRound: Int): Query {
        val baseQuery = buildBaseQuery(filters.copy(status = null))
        buildEffectiveStatusCriteria(filters.status, currentRound)?.let(baseQuery::addCriteria)
        return baseQuery
    }

    private fun buildUiSectionQuery(
        section: ChallengeUiSection,
        wallet: String,
        runtimeContext: ChallengeRuntimeContext,
    ): Query = Query(buildUiSectionCriteria(section, wallet, runtimeContext))

    private fun buildUiSectionCriteria(
        section: ChallengeUiSection,
        wallet: String,
        runtimeContext: ChallengeRuntimeContext,
    ): Criteria {
        val currentRound = runtimeContext.currentRound
        val effectiveActiveCriteria =
            buildEffectiveStatusCriteria(ChallengeStatus.Active, currentRound)
                ?: error("Active criteria should be available")
        val effectiveInvalidCriteria =
            buildEffectiveStatusCriteria(ChallengeStatus.Invalid, currentRound)
                ?: error("Invalid criteria should be available")

        return when (section) {
            ChallengeUiSection.NeededActions ->
                orCriteria(
                    andCriteria(
                        buildPendingBeforeStartCriteria(currentRound),
                        Criteria.where(B3trChallenge::invited.name).`is`(wallet),
                        Criteria.where(B3trChallenge::declined.name).ne(wallet),
                    ),
                    andCriteria(
                        buildViewerRelevanceCriteria(wallet),
                        effectiveActiveCriteria,
                        Criteria.where(B3trChallenge::endRound.name).lt(currentRound),
                    ),
                    andCriteria(
                        Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Finalized),
                        Criteria.where(B3trChallenge::claimedBy.name).ne(wallet),
                        orCriteria(
                            andCriteria(
                                Criteria.where(B3trChallenge::settlementMode.name)
                                    .`is`(SettlementMode.CreatorRefund),
                                Criteria.where(B3trChallenge::creator.name).`is`(wallet),
                            ),
                            andCriteria(
                                Criteria.where(B3trChallenge::settlementMode.name)
                                    .ne(SettlementMode.CreatorRefund),
                                Criteria.where(B3trChallenge::participants.name).`is`(wallet),
                            ),
                        ),
                    ),
                    andCriteria(
                        Criteria.where(B3trChallenge::refundedBy.name).ne(wallet),
                        orCriteria(
                            andCriteria(
                                Criteria.where(B3trChallenge::kind.name).`is`(ChallengeKind.Stake),
                                Criteria.where(B3trChallenge::participants.name).`is`(wallet),
                            ),
                            andCriteria(
                                Criteria.where(B3trChallenge::kind.name)
                                    .`is`(ChallengeKind.Sponsored),
                                Criteria.where(B3trChallenge::creator.name).`is`(wallet),
                            ),
                        ),
                        orCriteria(
                            Criteria.where(B3trChallenge::status.name)
                                .`is`(ChallengeStatus.Cancelled),
                            effectiveInvalidCriteria,
                        ),
                    ),
                )
            ChallengeUiSection.Active ->
                andCriteria(
                    buildViewerRelevanceCriteria(wallet),
                    orCriteria(
                        buildPendingBeforeStartCriteria(currentRound),
                        andCriteria(
                            effectiveActiveCriteria,
                            Criteria.where(B3trChallenge::endRound.name).gte(currentRound),
                        ),
                    ),
                )
            ChallengeUiSection.Open ->
                andCriteria(
                    buildPendingBeforeStartCriteria(currentRound),
                    Criteria.where(B3trChallenge::visibility.name).`is`(ChallengeVisibility.Public),
                    Criteria.where(B3trChallenge::participantCount.name)
                        .lt(runtimeContext.maxParticipants),
                    Criteria.where(B3trChallenge::creator.name).ne(wallet),
                    Criteria.where(B3trChallenge::participants.name).ne(wallet),
                )
            ChallengeUiSection.Explore ->
                andCriteria(
                    effectiveActiveCriteria,
                    Criteria.where(B3trChallenge::visibility.name).`is`(ChallengeVisibility.Public),
                    Criteria.where(B3trChallenge::endRound.name).gte(currentRound),
                    Criteria.where(B3trChallenge::creator.name).ne(wallet),
                    Criteria.where(B3trChallenge::participants.name).ne(wallet),
                )
            ChallengeUiSection.History ->
                orCriteria(
                    andCriteria(
                        buildPendingBeforeStartCriteria(currentRound),
                        Criteria.where(B3trChallenge::declined.name).`is`(wallet),
                        Criteria.where(B3trChallenge::participantCount.name)
                            .lt(runtimeContext.maxParticipants),
                    ),
                    andCriteria(
                        buildViewerRelevanceCriteria(wallet),
                        buildEffectiveDoneCriteria(currentRound),
                    ),
                )
        }
    }

    private fun buildEffectiveStatusCriteria(
        status: ChallengeStatus?,
        currentRound: Int,
    ): Criteria? =
        when (status) {
            null -> null
            ChallengeStatus.Pending -> buildPendingBeforeStartCriteria(currentRound)
            ChallengeStatus.Active ->
                orCriteria(
                    Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Active),
                    buildPendingBecameActiveCriteria(currentRound),
                )
            ChallengeStatus.Finalized ->
                Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Finalized)
            ChallengeStatus.Cancelled ->
                Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Cancelled)
            ChallengeStatus.Invalid ->
                orCriteria(
                    Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Invalid),
                    buildPendingBecameInvalidCriteria(currentRound),
                )
        }

    private fun buildPendingBeforeStartCriteria(currentRound: Int): Criteria =
        andCriteria(
            Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Pending),
            Criteria.where(B3trChallenge::startRound.name).gt(currentRound),
        )

    private fun buildPendingBecameActiveCriteria(currentRound: Int): Criteria =
        andCriteria(
            Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Pending),
            Criteria.where(B3trChallenge::startRound.name).lte(currentRound),
            orCriteria(
                andCriteria(
                    Criteria.where(B3trChallenge::kind.name).`is`(ChallengeKind.Stake),
                    Criteria.where(B3trChallenge::participantCount.name).gte(2),
                ),
                andCriteria(
                    Criteria.where(B3trChallenge::kind.name).`is`(ChallengeKind.Sponsored),
                    Criteria.where(B3trChallenge::participantCount.name).gte(1),
                ),
            ),
        )

    private fun buildPendingBecameInvalidCriteria(currentRound: Int): Criteria =
        andCriteria(
            Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Pending),
            Criteria.where(B3trChallenge::startRound.name).lte(currentRound),
            orCriteria(
                andCriteria(
                    Criteria.where(B3trChallenge::kind.name).`is`(ChallengeKind.Stake),
                    Criteria.where(B3trChallenge::participantCount.name).lt(2),
                ),
                andCriteria(
                    Criteria.where(B3trChallenge::kind.name).`is`(ChallengeKind.Sponsored),
                    Criteria.where(B3trChallenge::participantCount.name).lt(1),
                ),
            ),
        )

    private fun buildEffectiveDoneCriteria(currentRound: Int): Criteria =
        orCriteria(
            Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Finalized),
            Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Cancelled),
            buildPendingBecameInvalidCriteria(currentRound),
            Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Invalid),
        )

    private fun buildViewerRelevanceCriteria(wallet: String): Criteria =
        orCriteria(
            Criteria.where(B3trChallenge::creator.name).`is`(wallet),
            Criteria.where(B3trChallenge::participants.name).`is`(wallet),
        )

    private fun buildUiResponse(
        challenge: B3trChallenge,
        wallet: String,
        runtimeContext: ChallengeRuntimeContext,
        participantActions: BigInteger = BigInteger.ZERO,
    ): B3trChallengeUiResponse {
        val viewerContext = buildViewerContext(challenge, wallet, runtimeContext.currentRound)
        val uiState =
            buildUiState(
                challenge = challenge,
                runtimeContext = runtimeContext,
                viewerContext = viewerContext,
                participantActions = participantActions,
            )
        return B3trChallengeUiResponse.from(challenge, uiState)
    }

    private fun andCriteria(vararg criteria: Criteria): Criteria =
        if (criteria.size == 1) criteria.single() else Criteria().andOperator(*criteria)

    private fun orCriteria(vararg criteria: Criteria): Criteria =
        if (criteria.size == 1) criteria.single() else Criteria().orOperator(*criteria)

    private fun buildViewerContext(
        challenge: B3trChallenge,
        wallet: String,
        currentRound: Int,
    ): ChallengeViewerContext {
        val status = computeEffectiveStatus(challenge, currentRound)
        val viewerStatus = determineViewerStatus(challenge, wallet)
        val isCreator = challenge.creator == wallet
        val isJoined = viewerStatus == ParticipantStatus.Joined
        val viewerEligible = challenge.eligibleInvitees.contains(wallet)
        val isInvited = viewerStatus == ParticipantStatus.Invited || viewerEligible
        val isInvitationPending = status == ChallengeStatus.Pending && isInvited && !isJoined

        return ChallengeViewerContext(
            status = status,
            viewerStatus = viewerStatus,
            isCreator = isCreator,
            isJoined = isJoined,
            isInvitationPending = isInvitationPending,
            hasClaimed = challenge.claimedBy.contains(wallet),
            hasRefunded = challenge.refundedBy.contains(wallet),
        )
    }

    private fun determineViewerStatus(challenge: B3trChallenge, wallet: String): ParticipantStatus =
        when {
            challenge.participants.contains(wallet) -> ParticipantStatus.Joined
            challenge.declined.contains(wallet) -> ParticipantStatus.Declined
            challenge.invited.contains(wallet) -> ParticipantStatus.Invited
            else -> ParticipantStatus.None
        }

    private fun getParticipantActions(
        candidates: List<B3trChallenge>,
        viewerContexts: Map<Long, ChallengeViewerContext>,
        wallet: String,
    ): Map<Long, BigInteger> {
        val claimableCandidates =
            candidates.filter { challenge ->
                val viewerContext = viewerContexts.getValue(challenge.challengeId)
                needsParticipantActions(challenge, viewerContext)
            }

        if (claimableCandidates.isEmpty()) {
            return emptyMap()
        }

        val clauses =
            claimableCandidates.map {
                createClause(
                    challengesContract,
                    Function(
                        "getParticipantActions",
                        listOf(Uint256(BigInteger.valueOf(it.challengeId)), AbiAddress(wallet)),
                        emptyList(),
                    ),
                )
            }
        val responses = runBlocking {
            thorClient.inspectClauses(clauses, BlockRevision.Keyword.BEST)
        }
        require(responses.size == claimableCandidates.size) {
            "Unexpected participant actions response count: ${responses.size}"
        }

        return claimableCandidates
            .mapIndexed { index, challenge ->
                challenge.challengeId to decodeUint256(responses[index].data)
            }
            .toMap()
    }

    private fun needsParticipantActions(
        challenge: B3trChallenge,
        viewerContext: ChallengeViewerContext,
    ): Boolean =
        viewerContext.status == ChallengeStatus.Finalized &&
            !viewerContext.hasClaimed &&
            viewerContext.isJoined &&
            challenge.settlementMode != SettlementMode.CreatorRefund

    private fun buildUiState(
        challenge: B3trChallenge,
        runtimeContext: ChallengeRuntimeContext,
        viewerContext: ChallengeViewerContext,
        participantActions: BigInteger,
    ): ChallengeUiState {
        val hasReachedParticipantLimit =
            challenge.participantCount >= runtimeContext.maxParticipants
        val canJoin =
            viewerContext.status == ChallengeStatus.Pending &&
                challenge.visibility == ChallengeVisibility.Public &&
                !viewerContext.isJoined &&
                !viewerContext.isCreator &&
                !hasReachedParticipantLimit
        val canAccept = viewerContext.isInvitationPending && !hasReachedParticipantLimit
        val canDecline =
            viewerContext.isInvitationPending &&
                viewerContext.viewerStatus != ParticipantStatus.Declined
        val canLeave =
            viewerContext.status == ChallengeStatus.Pending &&
                viewerContext.isJoined &&
                !viewerContext.isCreator
        val canCancel = viewerContext.status == ChallengeStatus.Pending && viewerContext.isCreator
        val canAddInvites =
            viewerContext.status == ChallengeStatus.Pending &&
                challenge.visibility == ChallengeVisibility.Private &&
                viewerContext.isCreator &&
                runtimeContext.currentRound < challenge.startRound
        val canClaim =
            !viewerContext.hasClaimed &&
                viewerContext.status == ChallengeStatus.Finalized &&
                when (challenge.settlementMode) {
                    SettlementMode.CreatorRefund -> viewerContext.isCreator
                    SettlementMode.QualifiedSplit ->
                        viewerContext.isJoined && participantActions >= challenge.threshold
                    else -> viewerContext.isJoined && participantActions == challenge.bestScore
                }
        val canRefund =
            !viewerContext.hasRefunded &&
                (viewerContext.status == ChallengeStatus.Cancelled ||
                    viewerContext.status == ChallengeStatus.Invalid) &&
                if (challenge.kind == ChallengeKind.Stake) {
                    viewerContext.isJoined
                } else {
                    viewerContext.isCreator
                }
        val isAwaitingFinalization =
            viewerContext.status == ChallengeStatus.Active &&
                challenge.endRound < runtimeContext.currentRound
        val canFinalize =
            isAwaitingFinalization && (viewerContext.isCreator || viewerContext.isJoined)

        return ChallengeUiState(
            status = viewerContext.status,
            maxParticipants = runtimeContext.maxParticipants,
            viewerStatus = viewerContext.viewerStatus,
            isCreator = viewerContext.isCreator,
            isJoined = viewerContext.isJoined,
            isInvitationPending = viewerContext.isInvitationPending,
            isAwaitingFinalization = isAwaitingFinalization,
            canJoin = canJoin,
            canLeave = canLeave,
            canAccept = canAccept,
            canDecline = canDecline,
            canCancel = canCancel,
            canAddInvites = canAddInvites,
            canClaim = canClaim,
            canRefund = canRefund,
            canFinalize = canFinalize,
        )
    }

    private fun belongsToSection(section: ChallengeUiSection, uiState: ChallengeUiState): Boolean {
        val needsPastAction = uiState.canClaim || uiState.canRefund || uiState.canFinalize
        val isLive =
            uiState.status == ChallengeStatus.Pending || uiState.status == ChallengeStatus.Active
        val isDone =
            uiState.status == ChallengeStatus.Finalized ||
                uiState.status == ChallengeStatus.Cancelled ||
                uiState.status == ChallengeStatus.Invalid

        return when (section) {
            ChallengeUiSection.NeededActions ->
                needsPastAction ||
                    ((uiState.canAccept || uiState.canDecline) &&
                        uiState.viewerStatus != ParticipantStatus.Declined)
            ChallengeUiSection.Active ->
                isLive && !uiState.isAwaitingFinalization && (uiState.isCreator || uiState.isJoined)
            ChallengeUiSection.Open -> uiState.canJoin
            ChallengeUiSection.Explore ->
                uiState.status == ChallengeStatus.Active &&
                    !uiState.isAwaitingFinalization &&
                    !uiState.isCreator &&
                    !uiState.isJoined
            ChallengeUiSection.History ->
                (uiState.viewerStatus == ParticipantStatus.Declined && uiState.canAccept) ||
                    (isDone && (uiState.isCreator || uiState.isJoined) && !needsPastAction)
        }
    }

    private fun getCurrentRound(): Int = runBlocking {
        val responses =
            thorClient.inspectClauses(
                listOf(createClause(xAllocVotingContract, currentRoundFunction)),
                BlockRevision.Keyword.BEST,
            )
        decodeUint256(responses.firstOrNull()?.data).toInt()
    }

    private fun getChallengeRuntimeContext(): ChallengeRuntimeContext = runBlocking {
        requireConfiguredAddress(challengesContract, "CHALLENGES_CONTRACT")
        val responses =
            thorClient.inspectClauses(
                listOf(
                    createClause(xAllocVotingContract, currentRoundFunction),
                    createClause(challengesContract, maxParticipantsFunction),
                ),
                BlockRevision.Keyword.BEST,
            )
        require(responses.size == 2) {
            "Unexpected challenge runtime response count: ${responses.size}"
        }
        ChallengeRuntimeContext(
            currentRound = decodeUint256(responses[0].data).toInt(),
            maxParticipants = decodeUint256(responses[1].data).toInt(),
        )
    }

    private fun createClause(address: String, function: Function): Clause =
        Clause(to = address, data = FunctionEncoder.encode(function), value = "0x0")

    private fun decodeUint256(data: String?): BigInteger {
        if (data.isNullOrBlank() || data == "0x") {
            return BigInteger.ZERO
        }

        return BigInteger(data.removePrefix("0x"), 16)
    }

    private fun requireConfiguredAddress(address: String, name: String) {
        require(address.isNotBlank() && !address.equals(ZERO_ADDRESS, ignoreCase = true)) {
            "$name not configured"
        }
    }

    private fun normalizeWallet(wallet: Address?): String =
        wallet?.value?.let(HexUtils::normalise) ?: ""
}

private data class ChallengeFilters(
    val status: ChallengeStatus?,
    val kind: ChallengeKind?,
    val visibility: ChallengeVisibility?,
    val creator: String?,
    val participant: String?,
    val invitee: String?,
    val appId: String?,
    val startRound: Int?,
    val endRound: Int?,
)

private data class ChallengeRuntimeContext(val currentRound: Int, val maxParticipants: Int)

private data class ChallengeViewerContext(
    val status: ChallengeStatus,
    val viewerStatus: ParticipantStatus,
    val isCreator: Boolean,
    val isJoined: Boolean,
    val isInvitationPending: Boolean,
    val hasClaimed: Boolean,
    val hasRefunded: Boolean,
)

private enum class ChallengeUiSection {
    NeededActions,
    Active,
    Open,
    Explore,
    History,
}

private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
