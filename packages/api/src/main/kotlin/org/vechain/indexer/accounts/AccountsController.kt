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
import org.vechain.indexer.constants.ACCOUNTS_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("accounts")
@Tag(name = "Accounts", description = "VeChain Thor Accounts Overview")
@Validated
@RestController
@RequestMapping(ACCOUNTS_PATH)
open class AccountsController(private val accountsService: AccountsService) {
    @GetMapping("/totals")
    @Operation(
        summary = "Retrieve total unique accounts overview",
        description =
            """
            Retrieves historical totals of unique VeChain accounts tracked per time frame (DAY, WEEK, MONTH, YEAR).
            The "ALL" account aggregates totals across all time frames.
            
            If no `timeFrame` is provided, the response defaults to showing the full cumulative totals (ALL).
        """,
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

    @GetMapping("/overview/{address}")
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
