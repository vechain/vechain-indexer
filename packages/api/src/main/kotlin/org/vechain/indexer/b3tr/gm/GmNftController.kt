package org.vechain.indexer.b3tr.gm

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.GM_NFT_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.GmNftLevelParameter
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy

@Profile("b3tr", "b3tr-gm-nft")
@Tag(
    name = "B3TR - Galaxy Member NFTs",
    description = "Endpoints for Galaxy Member level statistics.",
)
@Validated
@RestController
@RequestMapping(GM_NFT_PATH)
open class GmNftController(private val gmNftService: GmNftService) {
    @Operation(
        summary = "Get B3TR GM Level UserAllTimeActionSummary",
        description =
            "Returns the current overview (holders, B3TR donated, etc.) for each Galaxy Member level.",
    )
    @GetMapping("/level-overview")
    @GmNftLevelParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.HOURLY)
    open fun getLevelOverviews(
        @RequestParam(required = false) level: GmLevelName?
    ): List<GMLevelOverview> =
        if (level == null || level == GmLevelName.ALL) {
            gmNftService.levelOverviews()
        } else {
            listOf(gmNftService.levelOverview(level))
        }
}
