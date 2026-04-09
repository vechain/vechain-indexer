package org.vechain.indexer.b3tr.challenges

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
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
@Tag(name = "B3TR - Challenges", description = "Query indexed B3TR challenges.")
@Validated
@RestController
@RequestMapping(CHALLENGES_PATH)
open class ChallengesController(private val challengesService: ChallengesService) {
    @GetMapping
    @Operation(summary = "Get indexed B3TR challenges.")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(enumAsRef = true, implementation = ChallengeStatus::class),
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "kind",
        schema = Schema(enumAsRef = true, implementation = ChallengeKind::class),
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "visibility",
        schema = Schema(enumAsRef = true, implementation = ChallengeVisibility::class),
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getChallenges(
        @RequestParam(required = false) status: ChallengeStatus?,
        @RequestParam(required = false) kind: ChallengeKind?,
        @RequestParam(required = false) visibility: ChallengeVisibility?,
        @ValidAddress @RequestParam(required = false) creator: Address?,
        @ValidAddress @RequestParam(required = false) participant: Address?,
        @ValidAddress @RequestParam(required = false) invitee: Address?,
        @RequestParam(required = false) appId: String?,
        @RequestParam(required = false) startRound: Int?,
        @RequestParam(required = false) endRound: Int?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<B3trChallengeResponse> =
        challengesService.getChallenges(
            status = status,
            kind = kind,
            visibility = visibility,
            creator = creator,
            participant = participant,
            invitee = invitee,
            appId = appId,
            startRound = startRound,
            endRound = endRound,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trChallenge::createdAtBlockTimestamp.name,
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping("{challengeId}")
    @Operation(summary = "Get a single indexed B3TR challenge.")
    @CommonApiResponses
    open fun getChallenge(@PathVariable challengeId: Long): B3trChallengeDetailResponse =
        challengesService.getChallenge(challengeId)

    @GetMapping("needed-actions")
    @Operation(summary = "Get challenges that require viewer action.")
    @AddressParameter(name = "wallet", required = true)
    @CommonApiResponses
    @PaginationParameters
    open fun getNeededActionChallenges(
        @ValidAddress @RequestParam wallet: Address,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        challengesService.getNeededActionChallenges(
            wallet = wallet,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trChallenge::createdAtBlockTimestamp.name,
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping("active")
    @Operation(summary = "Get viewer active challenges.")
    @AddressParameter(name = "wallet", required = true)
    @CommonApiResponses
    @PaginationParameters
    open fun getActiveChallenges(
        @ValidAddress @RequestParam wallet: Address,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        challengesService.getActiveChallenges(
            wallet = wallet,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trChallenge::createdAtBlockTimestamp.name,
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping("open")
    @Operation(summary = "Get public pending challenges the viewer can join.")
    @AddressParameter(name = "wallet", required = true)
    @CommonApiResponses
    @PaginationParameters
    open fun getOpenChallenges(
        @ValidAddress @RequestParam wallet: Address,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        challengesService.getOpenChallenges(
            wallet = wallet,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trChallenge::createdAtBlockTimestamp.name,
                    B3trChallenge::challengeId.name,
                ),
        )

    @GetMapping("history")
    @Operation(summary = "Get viewer challenge history.")
    @AddressParameter(name = "wallet", required = true)
    @CommonApiResponses
    @PaginationParameters
    open fun getChallengeHistory(
        @ValidAddress @RequestParam wallet: Address,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<B3trChallengeUiResponse> =
        challengesService.getChallengeHistory(
            wallet = wallet,
            pageable =
                toPageable(
                    page,
                    size,
                    direction,
                    B3trChallenge::createdAtBlockTimestamp.name,
                    B3trChallenge::challengeId.name,
                ),
        )
}
