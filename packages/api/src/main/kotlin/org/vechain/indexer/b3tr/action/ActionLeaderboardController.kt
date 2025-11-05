package org.vechain.indexer.b3tr.action

import io.swagger.v3.oas.annotations.Operation
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
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.response.AppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.ERROR_CANT_PASS_ROUND_AND_DATE
import org.vechain.indexer.b3tr.action.response.UserAppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserLeaderboardItem
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.AppIdParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.CursorPaginationParameters
import org.vechain.indexer.docs.DateParameter
import org.vechain.indexer.docs.RoundIdParameter
import org.vechain.indexer.docs.SortByParameter
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.validation.ValidAppId
import org.vechain.indexer.validation.ValidISODateString
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "b3tr-actions")
@Tag(name = "B3TR - Action Leaderboards", description = "Leaderboards for B3TR Actions.")
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class ActionLeaderboardController(private val service: ActionLeaderboardService) {

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
    @RoundIdParameter
    @SortByParameter(
        Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"])
    )
    @CommonApiResponses
    @CursorPaginationParameters
    open fun getUserLeaderboard(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "actionsRewarded") sortBy: String,
        @RequestParam(required = false) cursor: String?,
    ): PaginatedResponse<UserLeaderboardItem> {

        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getUserRoundLeaderboard(roundId, size, direction, sortBy, cursor)
        }

        if (date != null) {
            return service.getUserDailyLeaderboard(date, size, direction, sortBy, cursor)
        }

        return service.getUserAllTimeLeaderboard(size, direction, sortBy, cursor)
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
    @RoundIdParameter
    @DateParameter
    @SortByParameter(
        Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"])
    )
    @CommonApiResponses
    @CursorPaginationParameters
    open fun getAppLeaderboard(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "actionsRewarded") sortBy: String,
        @RequestParam(required = false) cursor: String?,
    ): PaginatedResponse<AppLeaderboardItem> {

        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getAppRoundLeaderboard(roundId, size, direction, sortBy, cursor)
        }
        if (date != null) {
            return service.getAppDailyLeaderboard(date, size, direction, sortBy, cursor)
        }
        return service.getAppAllTimeLeaderboard(size, direction, sortBy, cursor)
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
    @AppIdParameter(required = true, `in` = ParameterIn.PATH)
    @RoundIdParameter
    @DateParameter
    @SortByParameter(
        Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"])
    )
    @CommonApiResponses
    @CursorPaginationParameters
    open fun getUserAppLeaderboard(
        @ValidAppId @PathVariable(required = true) appId: AppId,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "actionsRewarded") sortBy: String,
        @RequestParam(required = false) cursor: String?,
    ): PaginatedResponse<UserAppLeaderboardItem> {
        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getUserAppRoundLeaderboard(
                appId,
                roundId,
                size,
                direction,
                sortBy,
                cursor,
            )
        }

        if (date != null) {
            return service.getUserAppDailyLeaderboard(appId, date, size, direction, sortBy, cursor)
        }

        return service.getUserAppAllTimeLeaderboard(appId, size, direction, sortBy, cursor)
    }
}
