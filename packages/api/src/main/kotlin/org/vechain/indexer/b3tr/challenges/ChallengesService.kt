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

        return if (
            status == null ||
                status == ChallengeStatus.Finalized ||
                status == ChallengeStatus.Cancelled
        ) {
            findDirectPage(
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
        } else {
            findComputedStatusPage(
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

    open fun getChallenge(challengeId: Long): B3trChallengeDetailResponse {
        val challenge =
            repository.findByIdOrNull(B3trChallenge.documentId(challengeId))
                ?: throw ResourceNotFoundException("Challenge not found for id $challengeId")

        return B3trChallengeDetailResponse.from(
            challenge = challenge,
            status = computeEffectiveStatus(challenge, getCurrentRound()),
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
        val normalizedWallet = HexUtils.normalise(wallet.value)
        return findUiPage(section, normalizedWallet, runtimeContext, pageable)
    }

    private fun findDirectPage(
        filters: ChallengeFilters,
        currentRound: Int,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeResponse> {
        val query = buildBaseQuery(filters)
        query.with(pageable).limit(pageable.pageSize + 1)

        val results = mongoTemplate.find(query, B3trChallenge::class.java)
        val hasNext = results.size > pageable.pageSize
        val page = if (hasNext) results.dropLast(1) else results
        val slice =
            SliceImpl(
                page.map {
                    B3trChallengeResponse.from(it, computeEffectiveStatus(it, currentRound))
                },
                pageable,
                hasNext,
            )
        return paginatedResponse(slice)
    }

    private fun findComputedStatusPage(
        filters: ChallengeFilters,
        currentRound: Int,
        pageable: Pageable,
    ): PaginatedResponse<B3trChallengeResponse> {
        val pageSize = pageable.pageSize
        val requiredOffset = pageable.offset
        val chunkSize = maxOf(pageSize * 2, 100)
        val collected = mutableListOf<B3trChallengeResponse>()

        var skippedMatches = 0L
        var skipCandidates = 0L
        var hasMoreCandidates = true

        while (hasMoreCandidates && collected.size <= pageSize) {
            val query = buildComputedStatusQuery(filters)
            query.with(pageable.sort)
            query.skip(skipCandidates)
            query.limit(chunkSize)

            val candidates = mongoTemplate.find(query, B3trChallenge::class.java)
            hasMoreCandidates = candidates.size == chunkSize
            skipCandidates += candidates.size.toLong()

            candidates.forEach { candidate ->
                val computedStatus = computeEffectiveStatus(candidate, currentRound)
                if (computedStatus != filters.status) {
                    return@forEach
                }

                if (skippedMatches < requiredOffset) {
                    skippedMatches++
                    return@forEach
                }

                collected.add(B3trChallengeResponse.from(candidate, computedStatus))
                if (collected.size > pageSize) {
                    return@forEach
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

    private fun findUiPage(
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
            val query = buildUiSectionQuery(section, wallet, runtimeContext.currentRound)
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

            candidates.forEach { candidate ->
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
                    return@forEach
                }

                if (skippedMatches < requiredOffset) {
                    skippedMatches++
                    return@forEach
                }

                collected.add(B3trChallengeUiResponse.from(candidate, uiState))
                if (collected.size > pageSize) {
                    return@forEach
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

    private fun buildComputedStatusQuery(filters: ChallengeFilters): Query {
        val baseQuery = buildBaseQuery(filters.copy(status = null))
        val statusCriteria =
            when (filters.status) {
                ChallengeStatus.Pending ->
                    Criteria.where(B3trChallenge::status.name).`is`(ChallengeStatus.Pending)
                ChallengeStatus.Active ->
                    Criteria.where(B3trChallenge::status.name)
                        .`in`(listOf(ChallengeStatus.Pending, ChallengeStatus.Active))
                ChallengeStatus.Invalid ->
                    Criteria.where(B3trChallenge::status.name)
                        .`in`(listOf(ChallengeStatus.Pending, ChallengeStatus.Invalid))
                else -> null
            }

        if (statusCriteria == null) {
            return baseQuery
        }

        baseQuery.addCriteria(statusCriteria)
        return baseQuery
    }

    private fun buildUiSectionQuery(
        section: ChallengeUiSection,
        wallet: String,
        currentRound: Int,
    ): Query =
        when (section) {
            ChallengeUiSection.NeededActions ->
                buildViewerChallengeQuery(
                    wallet = wallet,
                    includeInvited = true,
                    includeDeclined = false,
                    statuses = null,
                )
            ChallengeUiSection.Active ->
                buildViewerChallengeQuery(
                    wallet = wallet,
                    includeInvited = false,
                    includeDeclined = false,
                    statuses = listOf(ChallengeStatus.Pending, ChallengeStatus.Active),
                )
            ChallengeUiSection.Open ->
                Query(
                    Criteria()
                        .andOperator(
                            Criteria.where(B3trChallenge::status.name)
                                .`is`(ChallengeStatus.Pending),
                            Criteria.where(B3trChallenge::visibility.name)
                                .`is`(ChallengeVisibility.Public),
                            Criteria.where(B3trChallenge::startRound.name).gt(currentRound),
                        )
                )
            ChallengeUiSection.Explore ->
                Query(
                    Criteria()
                        .andOperator(
                            Criteria.where(B3trChallenge::status.name)
                                .`in`(listOf(ChallengeStatus.Pending, ChallengeStatus.Active)),
                            Criteria.where(B3trChallenge::visibility.name)
                                .`is`(ChallengeVisibility.Public),
                            Criteria.where(B3trChallenge::startRound.name).lte(currentRound),
                            Criteria.where(B3trChallenge::endRound.name).gte(currentRound),
                        )
                )
            ChallengeUiSection.History ->
                buildViewerChallengeQuery(
                    wallet = wallet,
                    includeInvited = false,
                    includeDeclined = true,
                    statuses = null,
                )
        }

    private fun buildViewerChallengeQuery(
        wallet: String,
        includeInvited: Boolean,
        includeDeclined: Boolean,
        statuses: List<ChallengeStatus>?,
    ): Query {
        val walletCriteria =
            mutableListOf(
                Criteria.where(B3trChallenge::creator.name).`is`(wallet),
                Criteria.where(B3trChallenge::participants.name).`is`(wallet),
            )

        if (includeInvited) {
            walletCriteria.add(Criteria.where(B3trChallenge::invited.name).`is`(wallet))
        }
        if (includeDeclined) {
            walletCriteria.add(Criteria.where(B3trChallenge::declined.name).`is`(wallet))
        }

        val relevanceCriteria =
            if (walletCriteria.size == 1) {
                walletCriteria.single()
            } else {
                Criteria().orOperator(*walletCriteria.toTypedArray())
            }

        return if (statuses.isNullOrEmpty()) {
            Query(relevanceCriteria)
        } else {
            Query(
                Criteria()
                    .andOperator(
                        relevanceCriteria,
                        Criteria.where(B3trChallenge::status.name).`in`(statuses),
                    )
            )
        }
    }

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
        val canFinalize =
            viewerContext.status == ChallengeStatus.Active &&
                challenge.endRound < runtimeContext.currentRound

        return ChallengeUiState(
            status = viewerContext.status,
            maxParticipants = runtimeContext.maxParticipants,
            viewerStatus = viewerContext.viewerStatus,
            isCreator = viewerContext.isCreator,
            isJoined = viewerContext.isJoined,
            isInvitationPending = viewerContext.isInvitationPending,
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
                isLive && !uiState.canFinalize && (uiState.isCreator || uiState.isJoined)
            ChallengeUiSection.Open -> uiState.canJoin
            ChallengeUiSection.Explore ->
                uiState.status == ChallengeStatus.Active &&
                    !uiState.canFinalize &&
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
