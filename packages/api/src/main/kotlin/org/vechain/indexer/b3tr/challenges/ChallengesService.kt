package org.vechain.indexer.b3tr.challenges

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.challenges.repository.B3trChallengeRepository
import org.vechain.indexer.b3tr.challenges.repository.B3trUserChallengeRepository
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils

@Profile("b3tr", "b3tr-challenges")
@Service
open class ChallengesService(
    private val challengeRepository: B3trChallengeRepository,
    private val userChallengeRepository: B3trUserChallengeRepository,
) : IndexerService {
    /** Public challenges, optionally narrowed by [status]. No wallet scoping. */
    open fun getPublicChallenges(
        status: ChallengeStatus?,
        pageable: Pageable,
    ): PaginatedResponse<ChallengeSummaryResponse> {
        val results =
            if (status == null) {
                challengeRepository.findByVisibility(ChallengeVisibility.Public, pageable)
            } else {
                challengeRepository.findByVisibilityAndStatus(
                    ChallengeVisibility.Public,
                    status,
                    pageable,
                )
            }
        return paginatedResponse(results.map(ChallengeSummaryResponse::from))
    }

    /**
     * Wallet-scoped challenges bucketed by [filter]. OpenToJoin / OthersActive start from
     * `b3tr_challenges` (excluding challenges the wallet already participates in); the rest start
     * from `b3tr_user_challenges` and join the parent challenge document.
     */
    open fun getWalletChallenges(
        wallet: Address,
        filter: ChallengeFilter,
        pageable: Pageable,
    ): PaginatedResponse<ChallengeSummaryResponse> {
        val normalisedWallet = HexUtils.normalise(wallet.value)
        val results =
            when (filter) {
                ChallengeFilter.OpenToJoin ->
                    challengeRepository.findByVisibilityAndStatusExcludingIds(
                        ChallengeVisibility.Public,
                        ChallengeStatus.Pending,
                        challengeRepository.findUserChallengeIdsByWallet(normalisedWallet),
                        pageable,
                    )
                ChallengeFilter.OthersActive ->
                    challengeRepository.findByVisibilityAndStatusExcludingIds(
                        ChallengeVisibility.Public,
                        ChallengeStatus.Active,
                        challengeRepository.findUserChallengeIdsByWallet(normalisedWallet),
                        pageable,
                    )
                ChallengeFilter.NeededAction,
                ChallengeFilter.MyChallenges,
                ChallengeFilter.History ->
                    challengeRepository.findByFilter(normalisedWallet, filter, pageable)
            }
        return paginatedResponse(results.map(ChallengeSummaryResponse::from))
    }

    open fun getChallenge(challengeId: Long): ChallengeDetailResponse =
        challengeRepository.findByIdOrNull(B3trChallenge.documentId(challengeId))?.let {
            ChallengeDetailResponse.from(it)
        } ?: throw ResourceNotFoundException("Challenge not found for id $challengeId")

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf(
            "B3trChallenges" to (challengeRepository.getLatestRecord()?.blockNumber ?: 0),
            "B3trUserChallenges" to (userChallengeRepository.getLatestRecord()?.blockNumber ?: 0),
        )
}
