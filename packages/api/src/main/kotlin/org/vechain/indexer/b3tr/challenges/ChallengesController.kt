package org.vechain.indexer.b3tr.challenges

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.CHALLENGES_PATH
import org.vechain.indexer.constants.WALLET_CHALLENGES_PATH
import org.vechain.indexer.docs.ChallengeFilterParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "b3tr-challenges")
@Validated
@RestController
@Tag(name = "B3TR - Challenges", description = "Query indexed B3TR challenges.")
open class ChallengesController(private val challengesService: ChallengesService) {
    @GetMapping(CHALLENGES_PATH)
    @Operation(
        summary = "Get public indexed B3TR challenges.",
        description =
            "Returns public challenges across the network. Use `/api/v1/b3tr/users/{wallet}/challenges` for wallet-scoped views.",
    )
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getChallenges(
        @RequestParam(required = false) status: ChallengeStatus?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ChallengeSummaryResponse> =
        challengesService.getPublicChallenges(
            status = status,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trChallenge::createdAtBlockTimestamp.name,
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping(WALLET_CHALLENGES_PATH)
    @Operation(
        summary = "Get indexed B3TR challenges bucketed for a wallet.",
        description =
            "Returns challenges bucketed by a semantic `filter` that encodes both a challenge status set and the wallet's relationship to the challenge. See the `filter` parameter for the full list of buckets.",
    )
    @CommonApiResponses
    @PaginationParameters
    @ChallengeFilterParameter
    @CacheFor(CachePolicy.MINUTE)
    open fun getWalletChallenges(
        @ValidAddress @PathVariable wallet: Address,
        @RequestParam(required = true) filter: ChallengeFilter,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ChallengeSummaryResponse> =
        challengesService.getWalletChallenges(
            wallet = wallet,
            filter = filter,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    sortField(filter),
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping("$CHALLENGES_PATH/{challengeId}")
    @Operation(summary = "Get a single indexed B3TR challenge.")
    @CommonApiResponses
    @CacheFor(CachePolicy.MINUTE)
    open fun getChallenge(@PathVariable challengeId: Long): ChallengeDetailResponse =
        challengesService.getChallenge(challengeId)

    // OpenToJoin and OthersActive run against `b3tr_challenges`; sort by the challenge document's
    // own createdAt. Other filters start from `b3tr_user_challenges` and sort by its denormalised
    // per-user timestamp.
    private fun sortField(filter: ChallengeFilter): String =
        when (filter) {
            ChallengeFilter.OpenToJoin,
            ChallengeFilter.OthersActive -> B3trChallenge::createdAtBlockTimestamp.name
            ChallengeFilter.NeededAction,
            ChallengeFilter.MyChallenges,
            ChallengeFilter.History -> B3trUserChallenge::challengeCreatedAtBlockTimestamp.name
        }
}
