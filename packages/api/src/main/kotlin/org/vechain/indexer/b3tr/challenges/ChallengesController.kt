package org.vechain.indexer.b3tr.challenges

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.B3TR_PATH
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
    @Operation(summary = "Get public indexed B3TR challenges.")
    @CommonApiResponses
    @PaginationParameters
    open fun getChallenges(
        @RequestParam(required = false) phase: ChallengePhase?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ChallengeSummaryResponse> =
        challengesService.getChallenges(
            phase = phase,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trChallenge::createdAtBlockTimestamp.name,
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping("$CHALLENGES_PATH/{challengeId}")
    @Operation(summary = "Get a single public indexed B3TR challenge.")
    @CommonApiResponses
    open fun getChallenge(@PathVariable challengeId: Long): ChallengeDetailResponse =
        challengesService.getChallenge(challengeId)

    @GetMapping("$B3TR_PATH/users/{wallet}/challenges")
    @Operation(summary = "Get wallet-scoped indexed B3TR challenge states.")
    @AddressParameter(name = "wallet", required = true, `in` = ParameterIn.PATH)
    @Parameter(
        name = "type",
        description = "Optional wallet challenge list type: actionable, participating, history.",
        `in` = ParameterIn.QUERY,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getUserChallenges(
        @ValidAddress @PathVariable wallet: Address,
        @RequestParam(required = false) type: UserChallengeListType?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<UserChallengeStateResponse> =
        challengesService.getUserChallenges(
            wallet = wallet,
            type = type,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trUserChallenge::challengeCreatedAtBlockTimestamp.name,
                    B3trUserChallenge::challengeId.name,
                ),
        )

    @GetMapping("$B3TR_PATH/users/{wallet}/challenges/{challengeId}")
    @Operation(summary = "Get a single wallet-scoped indexed B3TR challenge state.")
    @AddressParameter(name = "wallet", required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    open fun getUserChallenge(
        @ValidAddress @PathVariable wallet: Address,
        @PathVariable challengeId: Long,
    ): UserChallengeStateResponse = challengesService.getUserChallenge(wallet, challengeId)
}
