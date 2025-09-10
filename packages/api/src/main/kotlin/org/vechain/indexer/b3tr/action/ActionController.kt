package org.vechain.indexer.b3tr.action

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.response.AppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.ERROR_CANT_PASS_ROUND_AND_DATE
import org.vechain.indexer.b3tr.action.response.UserAppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserOverview
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.exception.ExceptionResponse
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ISODateString
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAppId
import org.vechain.indexer.validation.ValidISODateString
import org.vechain.indexer.validation.ValidPageNumber
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "b3tr-actions")
@Tag(name = "B3TR - User Actions", description = "Endpoints for B3TR User Actions.")
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class ActionController(private val service: ActionService) {

    @GetMapping("/actions/users/{wallet}/overview")
    @Operation(
        summary =
            "Get B3TR action overview for a specific wallet, optionally for a specific round or date.",
        description =
            """
            This endpoint retrieves the B3TR action overview for a wallet address.
            Optionally, a roundId or a date can be provided to retrieve the overview for a specific round or date.

            - If roundId is provided, the overview for the specific round is returned.
            - If date is provided, the overview for the specific date is returned.
            - If roundId/date are not provided, the all time sustainability overview for the user is returned.
            - If both roundId and date are provided, a BadRequest error is returned.
        """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved the B3TR action overview",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = UserOverview::class),
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "400",
                    description = "Validation errors occurred, eg: invalid wallet address",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
                ApiResponse(
                    responseCode = "500",
                    description = "Service not available",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "wallet",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Wallet address of the user to filter by.",
        required = true,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "roundId",
        schema = Schema(type = "integer"),
        description = "Round ID to filter by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "date",
        schema = Schema(type = "string", format = "date", pattern = ISODateString.REGEX),
        description = "A date to filter by. In UTC, format: yyyy-MM-dd.",
        required = false,
    )
    open fun getUserOverview(
        @ValidAddress @PathVariable wallet: Address,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): UserOverview {
        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getRoundWalletOverview(wallet, roundId)
        }

        if (date != null) {
            return service.getDailyWalletOverview(wallet, date)
        }

        return service.getAllTimeWalletOverview(wallet)
    }

    @GetMapping("actions/leaderboards/users")
    @Operation(
        summary = "Get leaderboard of user's B3TR actions.",
        description =
            """
            This endpoint retrieves the user leaderboard based on their B3TR actions.
            
            - If roundId is provided, the leaderboard for the specific round is returned.
            - If date is provided, the leaderboard for the specific date is returned.
            - If neither roundId nor date are provided, the all-time leaderboard is returned.
            - If both roundId and date are provided, a BadRequest error is returned.
            
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "Validation errors occurred, eg: must provide wallet or roundId",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "roundId",
        schema = Schema(type = "integer"),
        description = "Round ID to filter by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "sortBy",
        description = "The sort by field",
        required = false,
        schema = Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"]),
    )
    @PaginationParameters
    open fun getUserLeaderboard(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageNumber @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "actionsRewarded") sortBy: String,
    ): PaginatedResponse<UserLeaderboardItem> {

        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getUserRoundLeaderboard(roundId, page, size, direction, sortBy)
        }

        if (date != null) {
            return service.getUserDailyLeaderboard(date, page, size, direction, sortBy)
        }

        return service.getUserAllTimeLeaderboard(page, size, direction, sortBy)
    }

    @GetMapping("actions/leaderboards/apps")
    @Operation(
        summary = "Get the app B3TR action leaderboard",
        description =
            """
            This endpoint retrieves the app B3TR action leaderboard.
            
            - If roundId is provided, the leaderboard for the specific round is returned.
            - If date is provided, the leaderboard for the specific date is returned.
            - If neither roundId nor date are provided, the all-time leaderboard is returned.
            - If both roundId and date are provided, a BadRequest error is returned.
            
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "Validation errors occurred, eg: must provide wallet or roundId",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "roundId",
        schema = Schema(type = "integer"),
        description = "Round ID to filter by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "date",
        schema = Schema(type = "string", format = "date", pattern = ISODateString.REGEX),
        description = "A date to filter by. In UTC, format: yyyy-MM-dd.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "sortBy",
        description = "The sort by field",
        required = false,
        schema = Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"]),
    )
    open fun getAppLeaderboard(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageNumber @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "actionsRewarded") sortBy: String,
    ): PaginatedResponse<AppLeaderboardItem> {

        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getAppRoundLeaderboard(roundId, page, size, direction, sortBy)
        }
        if (date != null) {
            return service.getAppDailyLeaderboard(date, page, size, direction, sortBy)
        }
        return service.getAppAllTimeLeaderboard(page, size, direction, sortBy)
    }

    @GetMapping("actions/leaderboards/apps/{appId}")
    @Operation(
        summary = "Get the user B3TR action leaderboard for a given app",
        description =
            """
            This endpoint retrieves the user B3TR action leaderboard for a given app.
            
            - If roundId is provided, the leaderboard for the specific round is returned.
            - If date is provided, the leaderboard for the specific date is returned.
            - If neither roundId nor date are provided, the all-time leaderboard is returned.
            - If both roundId and date are provided, a BadRequest error is returned.
            
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "Validation errors occurred, eg: must provide wallet or roundId",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "appId",
        description = "App ID to query by.",
        required = true,
        schema = Schema(type = "string", pattern = AppId.REGEX),
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "roundId",
        schema = Schema(type = "integer"),
        description = "Round ID to filter by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "date",
        schema = Schema(type = "string", format = "date", pattern = ISODateString.REGEX),
        description = "A date to filter by. In UTC, format: yyyy-MM-dd.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "sortBy",
        description = "The sort by field",
        required = false,
        schema = Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"]),
    )
    @PaginationParameters
    open fun getUserAppLeaderboard(
        @ValidAppId @PathVariable(required = true) appId: AppId,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageNumber @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "actionsRewarded") sortBy: String,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getUserAppRoundLeaderboard(appId, roundId, page, size, direction, sortBy)
        }

        if (date != null) {
            return service.getUserAppDailyLeaderboard(appId, date, page, size, direction, sortBy)
        }

        return service.getUserAppAllTimeLeaderboard(appId, page, size, direction, sortBy)
    }
}
