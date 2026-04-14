package org.vechain.indexer.b3tr.navigator

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.NAVIGATOR_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr")
@Tag(
    name = "B3TR Navigators",
    description =
        "Query VeBetterDAO navigators — professional voting delegates who stake B3TR and vote on behalf of citizens.",
)
@Validated
@RestController
@RequestMapping(NAVIGATOR_PATH)
open class NavigatorController(private val navigatorApiService: NavigatorApiService) {

    @GetMapping("/overview")
    @Operation(
        summary = "Get navigator overview",
        description =
            "Returns aggregate statistics across all active navigators: total count, total B3TR staked, total citizens delegating, and total VOT3 delegated.",
    )
    @CommonApiResponses
    open fun getOverview(): NavigatorOverview = navigatorApiService.getOverview()

    @GetMapping
    @Operation(
        summary = "Get navigators",
        description =
            "Returns a paginated list of navigators with their current state including stake, citizen count, and delegation totals. Supports filtering by status and address, and ordering by various fields.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getNavigators(
        @Parameter(
            description = "Filter by navigator address.",
            example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            schema = Schema(type = "string"),
        )
        @RequestParam(required = false)
        navigator: String?,
        @Parameter(
            description = "Filter by navigator status. Multiple values allowed.",
            array =
                ArraySchema(
                    schema =
                        Schema(
                            type = "string",
                            allowableValues = ["ACTIVE", "EXITING", "DEACTIVATED"],
                        )
                ),
        )
        @RequestParam(required = false)
        status: List<String>?,
        @Parameter(
            description = "Field to order results by.",
            schema =
                Schema(
                    type = "string",
                    allowableValues = ["stake", "totalDelegated", "citizenCount", "registeredAt"],
                    defaultValue = "registeredAt",
                ),
        )
        @RequestParam(required = false)
        orderBy: String?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<Navigator> {
        val sortField = resolveOrderBy(orderBy)
        val pageable = PaginationUtils.toPageable(page, size, direction, sortField, "_id")
        return paginatedResponse(
            navigatorApiService.findNavigators(
                navigator = navigator,
                statuses = parseStatuses(status),
                pageable = pageable,
            )
        )
    }

    @GetMapping("/citizens")
    @Operation(
        summary = "Get citizens delegated to a navigator",
        description =
            "Returns a paginated list of active citizen delegations for a specific navigator. Each entry includes the citizen address, delegated VOT3 amount, and delegation start date.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getCitizens(
        @Parameter(
            description = "Navigator address to list citizens for.",
            required = true,
            example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            schema = Schema(type = "string"),
        )
        @RequestParam
        navigator: String,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<NavigatorCitizen> {
        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                NavigatorCitizen::delegatedAt.name,
                "_id",
            )
        return paginatedResponse(
            navigatorApiService.findCitizens(navigator = navigator, pageable = pageable)
        )
    }

    @GetMapping("/delegations")
    @Operation(
        summary = "Get delegation event history",
        description =
            "Returns the full chronological history of delegation events (created, updated, removed) for a navigator or citizen. Each event includes the absolute amount and a delta field showing the change (positive for increases, negative for decreases).",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getDelegations(
        @Parameter(
            description = "Filter by navigator address.",
            example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            schema = Schema(type = "string"),
        )
        @RequestParam(required = false)
        navigator: String?,
        @Parameter(
            description = "Filter by citizen address.",
            example = "0x3f90bf8b314c42005103b3c94505634fa680dcee",
            schema = Schema(type = "string"),
        )
        @RequestParam(required = false)
        citizen: String?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<NavigatorDelegationEvent> {
        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                NavigatorDelegationEvent::blockTimestamp.name,
                NavigatorDelegationEvent::txId.name,
                "_id",
            )
        return paginatedResponse(
            navigatorApiService.findDelegationEvents(
                navigator = navigator,
                citizen = citizen,
                pageable = pageable,
            )
        )
    }

    @GetMapping("/fees/summary")
    @Operation(
        summary = "Get navigator fee summary",
        description =
            "Returns total fees earned and claimed for a navigator, or globally if no navigator specified.",
    )
    @CommonApiResponses
    open fun getFeeSummary(
        @Parameter(
            description = "Filter by navigator address. Omit for global totals.",
            example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            schema = Schema(type = "string"),
        )
        @RequestParam(required = false)
        navigator: String?
    ): NavigatorFeeSummary = navigatorApiService.getFeeSummary(navigator)

    @GetMapping("/fees/history")
    @Operation(
        summary = "Get per-round fee history",
        description =
            "Returns a paginated list of per-round fee records for a navigator, showing deposited amount, claim status, and unlock round.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getFeeHistory(
        @Parameter(
            description = "Navigator address.",
            required = true,
            example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            schema = Schema(type = "string"),
        )
        @RequestParam
        navigator: String,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<NavigatorFee> {
        val pageable =
            PaginationUtils.toPageable(page, size, direction, NavigatorFee::roundId.name, "_id")
        return paginatedResponse(
            navigatorApiService.findFeeHistory(navigator = navigator, pageable = pageable)
        )
    }

    private fun resolveOrderBy(orderBy: String?): String =
        when (orderBy?.lowercase()) {
            "stake" -> Navigator::stake.name
            "totaldelegated" -> Navigator::totalDelegated.name
            "citizencount" -> Navigator::citizenCount.name
            "registeredat" -> Navigator::registeredAt.name
            null -> Navigator::registeredAt.name
            else -> Navigator::registeredAt.name
        }

    private fun parseStatuses(raw: List<String>?): List<NavigatorStatus>? {
        if (raw.isNullOrEmpty()) return null
        return raw.mapNotNull { s ->
                NavigatorStatus.entries.find { it.name.equals(s.trim(), ignoreCase = true) }
            }
            .ifEmpty { null }
    }
}
