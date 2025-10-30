package org.vechain.indexer.b3tr.action

import DateParameter
import RoundIdParameter
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
import org.vechain.indexer.b3tr.AppId
import org.vechain.indexer.b3tr.action.response.AppOverview
import org.vechain.indexer.b3tr.action.response.ERROR_CANT_PASS_ROUND_AND_DATE
import org.vechain.indexer.b3tr.action.response.GlobalOverview
import org.vechain.indexer.b3tr.action.response.UserAppOverview
import org.vechain.indexer.b3tr.action.response.UserOverview
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.AppIdParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.WalletParameter
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ISODateString
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAppId
import org.vechain.indexer.validation.ValidISODateString
import org.vechain.indexer.validation.ValidPageNumber
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "b3tr-actions")
@Tag(name = "B3TR - Actions", description = "Endpoints for B3TR Actions.")
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class ActionController(private val service: ActionService) {

    @GetMapping("/actions/users/{wallet}")
    @Operation(summary = "Get B3TR actions for a user")
    @WalletParameter(required = true, `in` = ParameterIn.PATH)
    @AppIdParameter
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "after",
        schema = Schema(type = "long"),
        description = "Return transactions after this timestamp (Unix time in milliseconds).",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "before",
        schema = Schema(type = "long"),
        description = "Return transactions before this timestamp (Unix time in milliseconds).",
        required = false,
    )
    @PaginationParameters
    open fun getUserActions(
        @ValidAddress @PathVariable(required = true) wallet: Address,
        @ValidAppId @RequestParam(required = false) appId: AppId?,
        @RequestParam(required = false) after: Long?,
        @RequestParam(required = false) before: Long?,
        @ValidPageNumber @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<Action> {

        if (appId != null) {
            return service.getUserActionsForApp(
                wallet = wallet,
                appId = appId,
                after = after,
                before = before,
                page = page,
                size = size,
                direction = direction,
            )
        }

        return service.getUserActions(
            wallet = wallet,
            after = after,
            before = before,
            page = page,
            size = size,
            direction = direction,
        )
    }

    @GetMapping("/actions/apps/{appId}")
    @Operation(summary = "Get B3TR actions for an app")
    @AppIdParameter(required = true, `in` = ParameterIn.PATH)
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "after",
        schema = Schema(type = "long"),
        description = "Return transactions after this timestamp (Unix time in milliseconds).",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "before",
        schema = Schema(type = "long"),
        description = "Return transactions before this timestamp (Unix time in milliseconds).",
        required = false,
    )
    @PaginationParameters
    open fun getAppActions(
        @ValidAppId @PathVariable(required = true) appId: AppId,
        @RequestParam(required = false) after: Long?,
        @RequestParam(required = false) before: Long?,
        @ValidPageNumber @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<Action> {
        return service.getAppActions(
            appId = appId,
            after = after,
            before = before,
            page = page,
            size = size,
            direction = direction,
        )
    }

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
    @WalletParameter(required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    open fun getUserOverview(
        @ValidAddress @PathVariable wallet: Address,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): UserOverview {
        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getRoundUserOverview(wallet, roundId)
        }

        if (date != null) {
            return service.getDailyUserOverview(wallet, date)
        }

        return service.getAllTimeUserOverview(wallet)
    }

    @GetMapping("/actions/users/{wallet}/app/{appId}/overview")
    @Operation(
        summary =
            "Get B3TR action overview for a user on a specific app, optionally for a specific round or date.",
        description =
            """
            This endpoint retrieves the B3TR action overview for a user on a specific app.
            Optionally, a roundId or a date can be provided to retrieve the overview for a specific round or date.

            - If roundId is provided, the overview for the specific round is returned.
            - If date is provided, the overview for the specific date is returned.
            - If roundId/date are not provided, the all time sustainability overview for the user on the app is returned.
            - If both roundId and date are provided, a BadRequest error is returned.
        """,
    )
    @WalletParameter(required = true, `in` = ParameterIn.PATH)
    @AppIdParameter(required = true, `in` = ParameterIn.PATH)
    @DateParameter
    @CommonApiResponses
    open fun getUserAppOverview(
        @ValidAddress @PathVariable wallet: Address,
        @ValidAppId @PathVariable appId: AppId,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): UserAppOverview {
        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getRoundUserAppOverview(wallet, appId, roundId)
        }

        if (date != null) {
            return service.getDailyUserAppOverview(wallet, appId, date)
        }

        return service.getAllTimeUserAppOverview(wallet, appId)
    }

    @GetMapping("/actions/users/{wallet}/daily-summaries")
    @Operation(summary = "Get daily action summaries for a specific user within a specified range.")
    @WalletParameter(required = true, `in` = ParameterIn.PATH)
    @Parameter(
        name = "startDate",
        schema = Schema(type = "string", format = "date", pattern = ISODateString.REGEX),
        description = "A date to filter by. In UTC, format: yyyy-MM-dd.",
        required = true,
    )
    @Parameter(
        name = "endDate",
        schema = Schema(type = "string", format = "date", pattern = ISODateString.REGEX),
        description = "A date to filter by. In UTC, format: yyyy-MM-dd.",
        required = true,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getDailySummariesForRange(
        @ValidAddress @PathVariable wallet: Address,
        @ValidISODateString @RequestParam startDate: String,
        @ValidISODateString @RequestParam endDate: String,
        @ValidPageNumber @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<UserDailyActionSummary> {
        return service.getDailySummariesForRange(
            wallet = wallet,
            startDate = startDate,
            endDate = endDate,
            page = page,
            size = size,
            direction = direction,
        )
    }

    @GetMapping("/actions/apps/{appId}/overview")
    @Operation(
        summary =
            "Get B3TR action overview for a specific app, optionally for a specific round or date.",
        description =
            """
            This endpoint retrieves the B3TR action overview for an app.
            Optionally, a roundId or a date can be provided to retrieve the overview for a specific round or date.

            - If roundId is provided, the overview for the specific round is returned.
            - If date is provided, the overview for the specific date is returned.
            - If roundId/date are not provided, the all time sustainability overview for the app is returned.
            - If both roundId and date are provided, a BadRequest error is returned.
        """,
    )
    @AppIdParameter(required = true, `in` = ParameterIn.PATH)
    @DateParameter
    @CommonApiResponses
    open fun getAppOverview(
        @ValidAppId @PathVariable appId: AppId,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): AppOverview {
        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getAppRoundOverview(appId, roundId)
        }

        if (date != null) {
            return service.getAppDailyOverview(appId, date)
        }

        return service.getAppAllTimeOverview(appId)
    }

    @GetMapping("/actions/global/overview")
    @Operation(
        summary = "Get global B3TR action overview, optionally for a specific round or date.",
        description =
            """
            This endpoint retrieves the global B3TR action overview.
            Optionally, a roundId or a date can be provided to retrieve the overview for a specific round or date.
            - If roundId is provided, the overview for the specific round is returned.
            - If date is provided, the overview for the specific date is returned.
            - If roundId/date are not provided, the all time global sustainability overview is returned.
            - If both roundId and date are provided, a BadRequest error is returned.
        """,
    )
    @RoundIdParameter
    @DateParameter
    @CommonApiResponses
    open fun getGlobalOverview(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): GlobalOverview {
        if (roundId != null && date != null) {
            throw BadRequestException(ERROR_CANT_PASS_ROUND_AND_DATE)
        }

        if (roundId != null) {
            return service.getGlobalRoundOverview(roundId)
        }

        if (date != null) {
            return service.getGlobalDailyOverview(date)
        }

        return service.getGlobalAllTimeOverview()
    }
}
