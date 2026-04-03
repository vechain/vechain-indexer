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
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr")
@Tag(name = "B3TR Navigators", description = "Query navigators and their current state")
@Validated
@RestController
@RequestMapping(NAVIGATOR_PATH)
open class NavigatorController(private val navigatorApiService: NavigatorApiService) {

    @GetMapping
    @Operation(
        summary = "Get navigators",
        description =
            "Returns navigators with their current state. Filter by status (ACTIVE, EXITING, DEACTIVATED) or address.",
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getNavigators(
        @RequestParam(required = false) navigator: String?,
        @RequestParam(required = false) status: List<String>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<Navigator> {
        val pageable =
            PaginationUtils.toPageable(page, size, direction, Navigator::blockTimestamp.name, "_id")
        return paginatedResponse(
            navigatorApiService.findNavigators(
                navigator = navigator,
                statuses = parseStatuses(status),
                pageable = pageable,
            )
        )
    }

    private fun parseStatuses(raw: List<String>?): List<NavigatorStatus>? {
        if (raw.isNullOrEmpty()) return null
        return raw.mapNotNull { s ->
                NavigatorStatus.entries.find { it.name.equals(s.trim(), ignoreCase = true) }
            }
            .ifEmpty { null }
    }
}
