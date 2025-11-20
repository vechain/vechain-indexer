package org.vechain.indexer.accounts

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.ACCOUNTS_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.TimeFrameParameter
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils
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
    @TimeFrameParameter
    @CommonApiResponses
    open fun getTotalAccounts(
        @RequestParam(required = false) timeFrame: TimeFrame?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<Accounts> {
        val pageable =
            PaginationUtils.toPageable(page, size, direction, Accounts::blockTimestamp.name)
        val accounts = accountsService.getTotal(timeFrame, pageable)
        return paginatedResponse(accounts)
    }
}
