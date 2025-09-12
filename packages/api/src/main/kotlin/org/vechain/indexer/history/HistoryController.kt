package org.vechain.indexer.history

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.constants.HISTORY_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.ArrayValidationUtils
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize

@Profile("history")
@Tag(name = "History", description = "Query on-chain event history")
@Validated
@RestController
@RequestMapping(HISTORY_PATH)
open class HistoryController(private val historyService: HistoryService) {
    @GetMapping("{account}")
    @Operation(summary = "Get account history")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "account",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "A valid account address",
        required = true,
        example = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "searchBy",
        array =
            ArraySchema(
                schema =
                    Schema(
                        type = "string",
                        allowableValues = ["to", "from", "origin", "gasPayer"],
                        description =
                            "Fields to search by. Defaults to ['to', 'from', 'origin'] if not provided.",
                    )
            ),
        description = "Array of fields to search by.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "eventName",
        array = ArraySchema(schema = Schema(implementation = HistoryEventName::class)),
        description = "Filter by specific transaction names.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "contractAddress",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The contract address",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "after",
        schema = Schema(type = "long"),
        description =
            "Return transactions after and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "before",
        schema = Schema(type = "long"),
        description =
            "Return transactions before and including this timestamp (Unix time in seconds).",
        required = false,
    )
    @CommonApiResponses
    @PaginationParameters
    open fun getUsersHistory(
        @ValidAddress @PathVariable account: Address,
        @RequestParam(required = false) eventName: List<String>?,
        @RequestParam(required = false) searchBy: List<String>?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @RequestParam(required = false) after: Long?,
        @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedHistoryEvent> {
        // Validate query parameters
        val validatedEventNames =
            ArrayValidationUtils.validateArray(
                input = eventName,
                allowedValues = HistoryEventName.entries.map { it.name }.toSet(),
                fieldName = "eventName",
            )

        val validatedSearchFields =
            ArrayValidationUtils.validateArray(
                input = searchBy,
                allowedValues = setOf("to", "from", "origin", "gasPayer"),
                fieldName = "searchBy",
            )

        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size?.plus(1),
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        return paginatedResponse(
            historyService.findUserHistoryByFilters(
                account = account.value,
                eventNames = validatedEventNames,
                searchFields = validatedSearchFields,
                contractAddress = contractAddress,
                before = before,
                after = after,
                pageable = pageable,
            )
        )
    }
}
