package org.vechain.indexer.b3tr.action

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.response.AppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserAppLeaderboardItem
import org.vechain.indexer.b3tr.action.response.UserLeaderboardItem
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.AppIdParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.CursorPaginationParameters
import org.vechain.indexer.docs.DateParameter
import org.vechain.indexer.docs.RoundIdParameter
import org.vechain.indexer.docs.SortByParameter
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedFor
import org.vechain.indexer.validation.ValidAppId
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidISODateString
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidSortField

@Profile("b3tr", "b3tr-actions")
@Tag(name = "B3TR - Action Leaderboards", description = "Leaderboards for B3TR actions.")
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class ActionLeaderboardController(private val service: ActionLeaderboardService) {

    @GetMapping("actions/leaderboards/users")
    @Operation(
        summary = "Get the wallet leaderboard",
        description =
            "Wallets ranked by their B3TR actions. Pass `roundId` for one allocation round or" +
                " `date` for one day; omit both for all time. Passing both is a 400 error.",
    )
    @RoundIdParameter
    @SortByParameter(
        Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"])
    )
    @CommonApiResponses
    @CursorPaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getUserLeaderboard(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @ValidSortField(allowedValues = ["totalRewardAmount", "actionsRewarded"])
        @RequestParam(required = false, defaultValue = "actionsRewarded")
        sortBy: String,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<PaginatedResponse<UserLeaderboardItem>> {
        val period = requestedPeriod(roundId, date)
        return cachedFor(
            service.leaderboardPolicy(period),
            service.getUserLeaderboard(period, size, direction, sortBy, cursor),
        )
    }

    @GetMapping("actions/leaderboards/apps")
    @Operation(
        summary = "Get the app leaderboard",
        description =
            "Apps ranked by their B3TR actions. Pass `roundId` for one allocation round or " +
                "`date` for one day; omit both for all time. Passing both is a 400 error.",
    )
    @RoundIdParameter
    @DateParameter
    @SortByParameter(
        Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"])
    )
    @CommonApiResponses
    @CursorPaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getAppLeaderboard(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @ValidSortField(allowedValues = ["totalRewardAmount", "actionsRewarded"])
        @RequestParam(required = false, defaultValue = "actionsRewarded")
        sortBy: String,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<PaginatedResponse<AppLeaderboardItem>> {
        val period = requestedPeriod(roundId, date)
        return cachedFor(
            service.leaderboardPolicy(period),
            service.getAppLeaderboard(period, size, direction, sortBy, cursor),
        )
    }

    @GetMapping("actions/leaderboards/apps/{appId}")
    @Operation(
        summary = "Get the wallet leaderboard for one app",
        description =
            "Wallets ranked by their B3TR actions in one app. Pass `roundId` for one " +
                "allocation round or `date` for one day; omit both for all time. Passing both is " +
                "a 400 error.",
    )
    @AppIdParameter(required = true, `in` = ParameterIn.PATH)
    @RoundIdParameter
    @DateParameter
    @SortByParameter(
        Schema(type = "string", allowableValues = ["totalRewardAmount", "actionsRewarded"])
    )
    @CommonApiResponses
    @CursorPaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getUserAppLeaderboard(
        @ValidAppId @PathVariable(required = true) appId: AppId,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @ValidSortField(allowedValues = ["totalRewardAmount", "actionsRewarded"])
        @RequestParam(required = false, defaultValue = "actionsRewarded")
        sortBy: String,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<PaginatedResponse<UserAppLeaderboardItem>> {
        val period = requestedPeriod(roundId, date)
        return cachedFor(
            service.leaderboardPolicy(period),
            service.getUserAppLeaderboard(appId, period, size, direction, sortBy, cursor),
        )
    }
}
