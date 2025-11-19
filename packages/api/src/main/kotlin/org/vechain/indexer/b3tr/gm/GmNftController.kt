package org.vechain.indexer.b3tr.gm

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.constants.GM_NFT_PATH
import org.vechain.indexer.docs.CommonApiResponses

@Profile("b3tr", "b3tr-gm-nft")
@Tag(
    name = "B3TR - Galaxy Member NFTs",
    description = "Endpoints for Galaxy Member level statistics.",
)
@Validated
@RestController
@RequestMapping(GM_NFT_PATH)
open class GmNftController(private val gmNftRepository: GmNftRepository) {
    @Operation(
        summary = "Get B3TR GM Level UserAllTimeActionSummary",
        description =
            "Returns the current overview (holders, B3TR donated, etc.) for each Galaxy Member level.",
    )
    @GetMapping("/level-overview")
    @Parameter(
        name = "level",
        `in` = ParameterIn.QUERY,
        description = "Optional level to filter by",
        schema = Schema(implementation = GmLevelName::class),
    )
    @CommonApiResponses
    open fun getLevelOverviews(
        @RequestParam(required = false) level: GmLevelName?
    ): List<GMLevelOverview> =
        if (level == null || level.name == "ALL") {
            gmNftRepository.levelCounts()
        } else {
            listOf(GMLevelOverview(level, gmNftRepository.countByLevelAndOwnerNot(level)))
        }
}
