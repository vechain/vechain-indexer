package org.vechain.indexer.b3tr.treasury

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.TREASURY_PATH
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize

@Profile("b3tr")
@Tag(name = "B3TR Treasury", description = "Treasury B3TR transfers with categories")
@Validated
@RestController
@RequestMapping(TREASURY_PATH)
open class TreasuryTransferController(
    private val treasuryTransferService: TreasuryTransferService
) {

    @GetMapping("/transfers")
    @Operation(
        summary = "Get treasury B3TR transfers",
        description = "Returns B3TR token transfers to/from the treasury, classified by category.",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "category",
        schema = Schema(enumAsRef = true, implementation = TreasuryTransferCategory::class),
        description =
            "Filter by category: emission, surplus, gm_upgrade, grant, governance, in, out, other. " +
                "If omitted, returns all.",
    )
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    open fun getTreasuryTransfers(
        @RequestParam(required = false) category: String?,
        @ValidNonNegativeLong @RequestParam(required = false) after: Long?,
        @ValidNonNegativeLong @RequestParam(required = false) before: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TreasuryTransfer> {
        TimeValidationUtils.validateTimestamps(after, before)

        val categoryEnum =
            when {
                category == null || category.isBlank() -> null
                else ->
                    runCatching { TreasuryTransferCategory.valueOf(category.uppercase()) }
                        .getOrElse {
                            throw BadRequestException(
                                "Invalid category: $category. Valid values: ${TreasuryTransferCategory.entries.joinToString { it.name.lowercase() }}"
                            )
                        }
            }

        val pageable =
            PaginationUtils.toPageable(
                page,
                size,
                direction,
                TreasuryTransfer::blockTimestamp.name,
                TreasuryTransfer::txId.name,
                "_id",
            )

        return paginatedResponse(
            treasuryTransferService.find(
                category = categoryEnum,
                after = after,
                before = before,
                pageable = pageable,
            )
        )
    }
}
