package org.vechain.indexer.b3tr.vot3

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
import org.vechain.indexer.b3tr.vot3.response.Vot3RankResponse
import org.vechain.indexer.b3tr.vot3.response.Vot3RichlistItem
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.CursorPaginationParameters
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "vot3-balance")
@Tag(name = "B3TR - VOT3 Richlist", description = "VOT3 balance richlist and holder rank.")
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class Vot3RichlistController(private val service: Vot3RichlistService) {

    @GetMapping("vot3/richlist")
    @Operation(
        summary = "Get VOT3 richlist",
        description =
            """
            Returns the list of VOT3 holders sorted by balance descending, with cursor pagination.
            Only includes addresses with balance greater than zero.
            """,
    )
    @CommonApiResponses
    @CursorPaginationParameters
    open fun getRichlist(
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
    ): PaginatedResponse<Vot3RichlistItem> = service.getRichlist(size, direction, cursor)

    @GetMapping("vot3/richlist/{address}")
    @Operation(
        summary = "Get VOT3 rank for an address",
        description =
            """
            Returns the address's position in the VOT3 richlist (rank), total holders,
            and top percentage (e.g. "top 10%" means topPercentage <= 10).
            """,
    )
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "The address to get the VOT3 rank for.",
    )
    @CommonApiResponses
    open fun getAddressRank(@ValidAddress @PathVariable address: Address): Vot3RankResponse =
        service.getAddressRank(address.value)
}
