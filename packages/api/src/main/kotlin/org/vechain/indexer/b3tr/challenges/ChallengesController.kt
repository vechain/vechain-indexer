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
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
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
    @Operation(summary = "Get indexed B3TR challenges.")
    @CommonApiResponses
    @PaginationParameters
    @AddressParameter(name = "wallet", description = "Optional wallet address to filter by.")
    open fun getChallenges(
        @RequestParam(required = false) status: ChallengeStatus?,
        @ValidAddress @RequestParam(required = false) wallet: Address?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ChallengeSummaryResponse> =
        challengesService.getChallenges(
            status = status,
            wallet = wallet,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    if (wallet == null) {
                        B3trChallenge::createdAtBlockTimestamp.name
                    } else {
                        B3trUserChallenge::challengeCreatedAtBlockTimestamp.name
                    },
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping("$CHALLENGES_PATH/{challengeId}")
    @Operation(summary = "Get a single indexed B3TR challenge.")
    @CommonApiResponses
    open fun getChallenge(@PathVariable challengeId: Long): ChallengeDetailResponse =
        challengesService.getChallenge(challengeId)
}
