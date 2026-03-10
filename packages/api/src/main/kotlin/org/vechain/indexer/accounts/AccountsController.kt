package org.vechain.indexer.accounts

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
import org.vechain.indexer.constants.API_ROOT
import org.vechain.indexer.constants.API_VERSION
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

@Profile("accounts")
@Tag(name = "Accounts", description = "VeChain Thor Accounts Overview")
@Validated
@RestController
@RequestMapping(API_ROOT)
open class AccountsController(private val accountsService: AccountsService) {
    @Deprecated("Use /api/v2/accounts/totals instead.")
    @GetMapping("$API_VERSION/accounts/totals")
    @Operation(
        summary = "Retrieve total unique accounts overview (deprecated)",
        description =
            """
            Retrieves historical totals of unique VeChain accounts tracked per time frame (DAY, WEEK, MONTH, YEAR).
            The "ALL" account aggregates totals across all time frames.
            
            If no `timeFrame` is provided, the response defaults to showing the full cumulative totals (ALL).

            Deprecated: use `GET /api/v2/accounts/totals` for cumulative time-series responses.
        """,
        deprecated = true,
    )
    @Parameter(
        name = "timeFrame",
        `in` = ParameterIn.QUERY,
        description = "Time frame to query totals for (DAY, WEEK, MONTH, YEAR, ALL).",
        required = false,
        schema = Schema(implementation = AccountQueryTimeFrame::class),
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getTotalAccounts(
        @RequestParam(required = false) timeFrame: AccountQueryTimeFrame?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TotalAccounts> {
        val pageable =
            PaginationUtils.toPageable(page, size, direction, TotalAccounts::blockTimestamp.name)
        val accounts = accountsService.getTotal(timeFrame, pageable)
        return paginatedResponse(accounts)
    }

    @GetMapping("/v2/accounts/totals")
    @Operation(
        summary = "Get cumulative total account counts for a timestamp range",
        description =
            """
            Returns cumulative unique-account totals for the requested timestamp range.

            The API automatically determines the appropriate granularity from the range:
            RAW, HOURLY, DAILY, WEEKLY, or MONTHLY.

            For sampled ranges, the response includes the nearest records at or before the
            requested boundaries so cumulative charts remain continuous even when sampled
            points are sparse.

            Values are monotonic cumulative totals. To derive accounts added between two
            consecutive points, subtract the earlier `totalAccounts` from the later one.
        """,
    )
    @AfterParameter(name = "startTimestamp", required = true)
    @BeforeParameter(name = "endTimestamp", required = true)
    @CommonApiResponses
    open fun getTotalAccountsV2(
        @ValidNonNegativeLong @RequestParam startTimestamp: Long,
        @ValidNonNegativeLong @RequestParam endTimestamp: Long,
    ): List<AccountTotalsSeries> {
        TimeValidationUtils.validateTimestamps(startTimestamp, endTimestamp)
        return accountsService.getTotalSeries(startTimestamp, endTimestamp)
    }

    @GetMapping("$API_VERSION/accounts/overview/{address}")
    @Operation(
        summary = "Retrieve account overview with VTHO earnings",
        description =
            """
            Retrieves the account overview including VTHO earned from three sources:
            - Block rewards (pre-Hayabusa authority nodes + post-Hayabusa validators)
            - Passive VTHO generation from VET holdings (genesis to Hayabusa only)
            - Stargate VTHO claimed (delegation rewards)

            The response includes individual breakdowns and a computed total.
        """,
    )
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The address of the account to retrieve the overview for.",
    )
    @CommonApiResponses
    open fun getOverview(@ValidAddress @PathVariable address: Address): AccountOverviewResponse =
        accountsService.getOverviewWithVthoEarnings(address)
            ?: throw ResourceNotFoundException("Account overview not found for address $address")
}
