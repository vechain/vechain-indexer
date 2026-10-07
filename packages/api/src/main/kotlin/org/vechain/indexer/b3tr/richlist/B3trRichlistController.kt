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
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidCursor
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr", "b3tr-balance")
@Tag(
    name = "B3TR - Richlist",
    description = "B3TR and VOT3 holder rankings.",
)
@Validated
@RestController
@RequestMapping(B3TR_PATH)
open class B3trRichlistController(private val service: B3trRichlistService) {

    @GetMapping("richlist")
    @Operation(
        summary = "List the largest B3TR holders",
        description =
            "Holders ranked by balance, largest first. `scope` picks what counts: `ALL` " +
                "(default) adds VOT3 and B3TR together, `VOT3` or `B3TR` ranks one token. B3TR " +
                "locked in the VOT3 contract is left out so it isn't counted twice.",
    )
    @CommonApiResponses
    @CursorPaginationParameters
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getRichlist(
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @ValidCursor @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "ALL") scope: RichlistScope,
    ): PaginatedResponse<B3trRichlistItem> = service.getRichlist(size, direction, cursor, scope)

    @GetMapping("richlist/{address}")
    @Operation(
        summary = "Get an address's B3TR rank",
        description =
            "An address's rank, the number of holders, and the top percentage it falls in. " +
                "`scope` works as on the richlist.",
    )
    @AddressParameter(
        name = "address",
        `in` = ParameterIn.PATH,
        required = true,
        description = "Holder address.",
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getAddressRank(
        @ValidAddress @PathVariable address: Address,
        @RequestParam(required = false, defaultValue = "ALL") scope: RichlistScope,
    ): B3trRankResponse = service.getAddressRank(address.value, scope)
}
