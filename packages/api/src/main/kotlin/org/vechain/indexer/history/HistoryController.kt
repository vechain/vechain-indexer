package org.vechain.indexer.history

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
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
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.docs.AccountParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.ContractAddressParameter
import org.vechain.indexer.docs.EventNameParameter
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.SearchByParameter
import org.vechain.indexer.docs.TokenEventNameParameter
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidEventName
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidSearchBy
import org.vechain.indexer.validation.ValidTokenEventName
import org.vechain.indexer.validation.ValidTokenId

@RequestMapping(API_ROOT)
@Profile("history")
@Tag(name = "History", description = "Query on-chain event history")
@Validated
@RestController
open class HistoryController(private val historyService: HistoryService) {
    @Deprecated("This api is deprecated post hayabusa release")
    @GetMapping("$API_VERSION/history/{account}")
    @Operation(summary = "Get account history")
    @AccountParameter(required = true, `in` = ParameterIn.PATH)
    @SearchByParameter
    @EventNameParameter
    @ContractAddressParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getUsersHistory(
        @ValidAddress @PathVariable account: Address,
        @ValidEventName @RequestParam(required = false) eventName: List<String>?,
        @ValidSearchBy @RequestParam(required = false) searchBy: List<String>?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<HistoryEventDto> {
        val mappedInputs: List<String>? = HistoryUtils.mapInputToNew(eventName)

        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        val slice =
            historyService.findUserHistoryByFilters(
                account = account.value,
                eventNames = mappedInputs, // query on new/canonical names
                searchFields = searchBy,
                contractAddress = contractAddress,
                before = before,
                after = after,
                pageable = pageable,
            )

        // Convert Slice<IndexedHistoryEvent> -> Slice<HistoryEventDto> with legacy names
        val dtoSlice = slice.map { HistoryEventDto.fromIndexed(it, legacy = true) }

        return paginatedResponse(dtoSlice)
    }

    @GetMapping("/v2/history/{account}")
    @Operation(summary = "Get account history")
    @AccountParameter(required = true, `in` = ParameterIn.PATH)
    @SearchByParameter
    @EventNameParameter
    @ContractAddressParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getUsersHistoryV2(
        @ValidAddress @PathVariable account: Address,
        @ValidEventName @RequestParam(required = false) eventName: List<String>?,
        @ValidSearchBy @RequestParam(required = false) searchBy: List<String>?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedHistoryEvent> {
        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        return paginatedResponse(
            historyService.findUserHistoryByFilters(
                account = account.value,
                eventNames = eventName,
                searchFields = searchBy,
                contractAddress = contractAddress,
                before = before,
                after = after,
                pageable = pageable,
            )
        )
    }

    @GetMapping("v2/history/token/{tokenId}")
    @Operation(summary = "Get token history")
    @TokenIdParameter(required = true, `in` = ParameterIn.PATH)
    @TokenEventNameParameter
    @ContractAddressParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getTokenHistory(
        @ValidTokenId @PathVariable(required = true) tokenId: String,
        @ValidTokenEventName @RequestParam(required = false) eventName: List<String>?,
        @ValidAddress @RequestParam(required = false) contractAddress: Address?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int? = DEFAULT_PAGE_SIZE,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<IndexedHistoryEvent> {
        TimeValidationUtils.validateTimestamps(after, before)

        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                IndexedHistoryEvent::blockTimestamp.name,
            )

        return paginatedResponse(
            historyService.findTokenIdHistoryByFilters(
                tokenId = tokenId,
                eventNames = eventName,
                contractAddress = contractAddress,
                before = before,
                after = after,
                pageable = pageable,
            )
        )
    }
}
