package org.vechain.indexer.b3tr.challenges

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("b3tr", "b3tr-challenges")
@Service
open class ChallengesService(private val repository: ChallengeReadRepository) : IndexerService {
    /** Public challenges, optionally narrowed by [status]. No wallet scoping. */
    open fun getPublicChallenges(
        status: ChallengeStatus?,
        pageable: Pageable,
    ): PaginatedResponse<ChallengeSummaryResponse> =
        paginatedResponse(
            offsetSlice(pageable, B3trChallenge::createdAtBlockTimestamp.name) {
                    offset,
                    limit,
                    direction ->
                    repository.findPublic(status, offset, limit, direction)
                }
                .map(ChallengeSummaryResponse::from)
        )

    /** Challenges bucketed by [filter], from the wallet's own rows or from the ones without it. */
    open fun getWalletChallenges(
        wallet: Address,
        filter: ChallengeFilter,
        pageable: Pageable,
    ): PaginatedResponse<ChallengeSummaryResponse> {
        val normalisedWallet = HexUtils.normalise(wallet.value)
        val sortField =
            when (filter) {
                ChallengeFilter.OpenToJoin,
                ChallengeFilter.OthersActive -> B3trChallenge::createdAtBlockTimestamp.name
                else -> B3trUserChallenge::challengeCreatedAtBlockTimestamp.name
            }
        return paginatedResponse(
            offsetSlice(pageable, sortField) { offset, limit, direction ->
                    when (filter) {
                        ChallengeFilter.OpenToJoin ->
                            repository.findOpenTo(
                                normalisedWallet,
                                ChallengeStatus.Pending,
                                offset,
                                limit,
                                direction,
                            )
                        ChallengeFilter.OthersActive ->
                            repository.findOpenTo(
                                normalisedWallet,
                                ChallengeStatus.Active,
                                offset,
                                limit,
                                direction,
                            )
                        else ->
                            repository.findByFilter(
                                normalisedWallet,
                                filter,
                                offset,
                                limit,
                                direction,
                            )
                    }
                }
                .map(ChallengeSummaryResponse::from)
        )
    }

    open fun getChallenge(challengeId: Long): ChallengeDetailResponse =
        repository.findById(challengeId)?.let { ChallengeDetailResponse.from(it) }
            ?: throw ResourceNotFoundException("Challenge not found for id $challengeId")

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf(
            "B3trChallenges" to repository.latestBlockNumber(),
            "B3trUserChallenges" to repository.latestUserBlockNumber(),
        )
}
