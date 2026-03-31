package org.vechain.indexer.b3tr.navigator

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.NAVIGATOR_PATH
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr")
@Tag(name = "B3TR Navigators", description = "Navigator lifecycle events, delegations, and fees")
@Validated
@RestController
@RequestMapping(NAVIGATOR_PATH)
open class NavigatorController(private val navigatorApiService: NavigatorApiService) {

    @GetMapping("/events")
    @Operation(
        summary = "Get navigator lifecycle events",
        description =
            "Returns navigator registration, staking, exit, slash, metadata, and report events.",
    )
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getNavigatorEvents(
        @RequestParam(required = false) navigator: String?,
        @RequestParam(required = false) eventType: String?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<NavigatorEvent> {
        TimeValidationUtils.validateTimestamps(after, before)
        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                NavigatorEvent::blockTimestamp.name,
                NavigatorEvent::txId.name,
                "_id",
            )
        return paginatedResponse(
            navigatorApiService.findEvents(
                navigator = navigator,
                eventType = eventType,
                after = after,
                before = before,
                pageable = pageable,
            )
        )
    }

    @GetMapping("/delegations")
    @Operation(
        summary = "Get navigator delegation events",
        description =
            "Returns delegation created, updated, removed, and navigator vote cast events.",
    )
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getNavigatorDelegations(
        @RequestParam(required = false) citizen: String?,
        @RequestParam(required = false) navigator: String?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<NavigatorDelegation> {
        TimeValidationUtils.validateTimestamps(after, before)
        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                NavigatorDelegation::blockTimestamp.name,
                NavigatorDelegation::txId.name,
                "_id",
            )
        return paginatedResponse(
            navigatorApiService.findDelegations(
                citizen = citizen,
                navigator = navigator,
                after = after,
                before = before,
                pageable = pageable,
            )
        )
    }

    @GetMapping("/fees")
    @Operation(
        summary = "Get navigator fee events",
        description = "Returns fee deposited, claimed, and navigator fee taken events.",
    )
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getNavigatorFees(
        @RequestParam(required = false) navigator: String?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<NavigatorFee> {
        TimeValidationUtils.validateTimestamps(after, before)
        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                NavigatorFee::blockTimestamp.name,
                NavigatorFee::txId.name,
                "_id",
            )
        return paginatedResponse(
            navigatorApiService.findFees(
                navigator = navigator,
                after = after,
                before = before,
                pageable = pageable,
            )
        )
    }
}
