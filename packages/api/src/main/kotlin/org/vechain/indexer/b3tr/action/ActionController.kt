package org.vechain.indexer.b3tr.action

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
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
import org.vechain.indexer.b3tr.action.response.AppOverview
import org.vechain.indexer.b3tr.action.response.GlobalOverview
import org.vechain.indexer.b3tr.action.response.UserAppOverview
import org.vechain.indexer.b3tr.action.response.UserDailyActionSummary
import org.vechain.indexer.b3tr.action.response.UserOverview
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.AppIdParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.DateParameter
import org.vechain.indexer.docs.EndDateParameter
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.RoundIdParameter
import org.vechain.indexer.docs.StartDateParameter
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedFor
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidAppId
import org.vechain.indexer.validation.ValidISODateString
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageNumber
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "b3tr-actions")
@Tag(
    name = "B3TR - Actions",
    description = "Actions users were rewarded B3TR for in VeBetterDAO apps.",
)
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class ActionController(private val service: ActionService) {

    @GetMapping("/actions/users/{wallet}")
    @Operation(summary = "Get B3TR actions for a user")
    @AddressParameter(name = "wallet", required = true, `in` = ParameterIn.PATH)
    @AppIdParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.HOURLY)
    open fun getUserActions(
        @ValidAddress @PathVariable(required = true) wallet: Address,
        @ValidAppId @RequestParam(required = false) appId: AppId?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
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
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.HOURLY)
    open fun getAppActions(
        @ValidAppId @PathVariable(required = true) appId: AppId,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
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
        summary = "Get a wallet's action totals",
        description =
            "A wallet's action totals. Pass `roundId` for one allocation round or `date` for " +
                "one day; omit both for all time. Passing both is a 400 error.",
    )
    @AddressParameter(name = "wallet", required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    @CacheFor(CachePolicy.HOURLY)
    open fun getUserOverview(
        @ValidAddress @PathVariable wallet: Address,
        @RoundIdParameter @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): ResponseEntity<UserOverview> {
        val period = requestedPeriod(roundId, date)
        return cachedFor(
            service.overviewPolicy(period, CachePolicy.HOURLY),
            service.getUserOverview(wallet, period),
        )
    }

    @GetMapping("/actions/users/{wallet}/app/{appId}/overview")
    @Operation(
        summary = "Get a wallet's action totals in one app",
        description =
            "A wallet's action totals in one app. Pass `roundId` for one allocation round or " +
                "`date` for one day; omit both for all time. Passing both is a 400 error.",
    )
    @AddressParameter(name = "wallet", required = true, `in` = ParameterIn.PATH)
    @AppIdParameter(required = true, `in` = ParameterIn.PATH)
    @DateParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getUserAppOverview(
        @ValidAddress @PathVariable wallet: Address,
        @ValidAppId @PathVariable appId: AppId,
        @RoundIdParameter @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): ResponseEntity<UserAppOverview> {
        val period = requestedPeriod(roundId, date)
        return cachedFor(
            service.overviewPolicy(period, CachePolicy.VOLATILE),
            service.getUserAppOverview(wallet, appId, period),
        )
    }

    @GetMapping("/actions/users/{wallet}/daily-summaries")
    @Operation(summary = "Get a wallet's daily action totals over a date range")
    @AddressParameter(name = "wallet", required = true, `in` = ParameterIn.PATH)
    @StartDateParameter
    @EndDateParameter
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.HOURLY)
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
        summary = "Get an app's action totals",
        description =
            "An app's action totals. Pass `roundId` for one allocation round or `date` for " +
                "one day; omit both for all time. Passing both is a 400 error.",
    )
    @AppIdParameter(required = true, `in` = ParameterIn.PATH)
    @DateParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.HOURLY)
    open fun getAppOverview(
        @ValidAppId @PathVariable appId: AppId,
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): ResponseEntity<AppOverview> {
        val period = requestedPeriod(roundId, date)
        return cachedFor(
            service.overviewPolicy(period, CachePolicy.HOURLY),
            service.getAppOverview(appId, period),
        )
    }

    @GetMapping("/actions/global/overview")
    @Operation(
        summary = "Get action totals across all apps",
        description =
            "Action totals across all apps. Pass `roundId` for one allocation round or `date`" +
                " for one day; omit both for all time. Passing both is a 400 error.",
    )
    @RoundIdParameter
    @DateParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.DAILY)
    open fun getGlobalOverview(
        @RequestParam(required = false) roundId: Int?,
        @ValidISODateString @RequestParam(required = false) date: String?,
    ): ResponseEntity<GlobalOverview> {
        val period = requestedPeriod(roundId, date)
        return cachedFor(
            service.overviewPolicy(period, CachePolicy.DAILY),
            service.getGlobalOverview(period),
        )
    }
}
