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
    open fun getChallenges(
        phase: ChallengePhase?,
        pageable: Pageable,
    ): PaginatedResponse<ChallengeSummaryResponse> {
        val results =
            if (phase == null) {
                challengeRepository.findByVisibility(ChallengeVisibility.Public, pageable)
            } else {
                challengeRepository.findByVisibilityAndPhase(
                    ChallengeVisibility.Public,
                    phase,
                    pageable,
                )
            }

        return paginatedResponse(results.map(ChallengeSummaryResponse::from))
    }

    open fun getChallenge(challengeId: Long): ChallengeDetailResponse =
        challengeRepository.findByIdOrNull(B3trChallenge.documentId(challengeId))?.let {
            ChallengeDetailResponse.from(it)
        } ?: throw ResourceNotFoundException("Challenge not found for id $challengeId")

    open fun getUserChallenges(
        wallet: Address,
        pageable: Pageable,
    ): PaginatedResponse<UserChallengeRefResponse> {
        val normalizedWallet = HexUtils.normalise(wallet.value)
        return paginatedResponse(
            userChallengeRepository
                .findByWallet(normalizedWallet, pageable)
                .map(UserChallengeRefResponse::from)
        )
    }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf(
            "B3trChallenges" to (challengeRepository.getLatestRecord()?.blockNumber ?: 0),
            "B3trUserChallenges" to (userChallengeRepository.getLatestRecord()?.blockNumber ?: 0),
        )
}
