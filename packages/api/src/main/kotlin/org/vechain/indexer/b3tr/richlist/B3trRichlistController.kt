package org.vechain.indexer.b3tr.richlist

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
import org.vechain.indexer.b3tr.richlist.response.B3trRankResponse
import org.vechain.indexer.b3tr.richlist.response.B3trRichlistItem
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.CursorPaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "vot3-balance", "b3tr-balance")
@Tag(
    name = "B3TR - Richlist",
    description = "Combined VOT3 and B3TR balance richlist and holder rank.",
)
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class B3trRichlistController(private val service: B3trRichlistService) {

    @GetMapping("richlist")
    @Operation(
        summary = "Get B3TR richlist",
        description =
            """
            Returns the list of holders by balance descending, with cursor pagination.
            Use scope=ALL (default) for combined VOT3+B3TR, scope=VOT3 or scope=B3TR for a single token.
            B3TR held by the VOT3 contract is excluded when scope is B3TR or ALL.
            """,
    )
    @CommonApiResponses
    @CursorPaginationParameters
    open fun getRichlist(
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "ALL") scope: RichlistScope,
    ): PaginatedResponse<B3trRichlistItem> = service.getRichlist(size, direction, cursor, scope)

    @GetMapping("richlist/{address}")
    @Operation(
        summary = "Get B3TR rank for an address",
        description =
            """
            Returns the address's rank, total holders, and top percentage for the chosen scope.
            Use scope=ALL (default), scope=VOT3, or scope=B3TR.
            """,
    )
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The address to get the rank for.",
    )
    @CommonApiResponses
    open fun getAddressRank(
        @ValidAddress @PathVariable address: Address,
        @RequestParam(required = false, defaultValue = "ALL") scope: RichlistScope,
    ): B3trRankResponse = service.getAddressRank(address.value, scope)
}
