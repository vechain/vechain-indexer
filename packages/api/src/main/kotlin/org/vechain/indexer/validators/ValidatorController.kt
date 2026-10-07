@file:Suppress("DEPRECATION") // Mixes V1 (deprecated) and V1-only (block-rewards) endpoints.

package org.vechain.indexer.validator

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.constants.VALIDATORS_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.PriceOracleUnavailableResponse
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedByAge
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.PaginationUtils.withIdTieBreaker
import org.vechain.indexer.utils.SortFieldUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validators.AllValidatorsMissedBlocksResponse
import org.vechain.indexer.validators.MissedBlocksTimeframe
import org.vechain.indexer.validators.ValidatorMissedBlocksPercentage
import org.vechain.indexer.validators.ValidatorResponse
import org.vechain.indexer.validators.ValidatorService

@Profile("validator")
@Tag(name = "Validator", description = "Validators (deprecated version).")
@Validated
@RestController
@RequestMapping(VALIDATORS_PATH)
open class ValidatorController(private val service: ValidatorService) {
    @GetMapping
    @Operation(
        summary = "List validators (deprecated)",
        description =
            """
            **Deprecated:** use `GET /api/v2/validators`.

            Kept for existing clients, with these differences:

            - `online` and `totalRewards` are always null
            - `offlineBlocks` counts missed block slots, so its numbers differ from before
            - `sortBy=nft:<Level>` is ignored and the default sort is used

            Filter by `status` or `endorser` and sort with `sortBy`: `validatorTvl`, `totalTvl`,
            `blockProbability` or `delegatorTvl`.
            """,
        deprecated = true,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validatorId",
        description = "Deprecated: use GET /api/v2/validators/{validatorId} instead.",
        required = false,
        deprecated = true,
        schema = Schema(type = "string", pattern = Address.REGEX),
    )
    @AddressParameter(name = "endorser", description = "Endorser address.")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(type = "array", implementation = Status::class),
        description = "Only these statuses.",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "sortBy",
        description = "Field to sort by.",
        required = false,
        schema =
            Schema(
                type = "string",
                allowableValues =
                    [
                        "validatorTvl",
                        "totalTvl",
                        "blockProbability",
                        "delegatorTvl",
                        "nft:Strength",
                        "nft:Thunder",
                        "nft:Mjolnir",
                        "nft:VeThorX",
                        "nft:StrengthX",
                        "nft:ThunderX",
                        "nft:MjolnirX",
                        "nft:Dawn",
                        "nft:Lightning",
                        "nft:Flash",
                    ],
            ),
    )
    @CommonApiResponses
    @PriceOracleUnavailableResponse
    @PaginationParameters
    @CacheFor(CachePolicy.HOURLY)
    open fun getValidators(
        @RequestParam(required = false) endorser: String?,
        @RequestParam(required = false) validatorId: String?,
        @RequestParam(required = false) status: List<Status>?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "validatorTvl") sortBy: String,
    ): PaginatedResponse<ValidatorResponse> {
        val sortField = SortFieldUtils.getSortFieldValidator(sortBy)
        val pageable = withIdTieBreaker(toPageable(page, size, direction, sortField))

        val results =
            service.getValidators(
                validatorId = validatorId?.let { HexUtils.normalise(it) },
                endorser = endorser?.let { HexUtils.normalise(it) },
                statuses = status,
                pageable = pageable,
            )

        return paginatedResponse(results)
    }

    @GetMapping("/block-rewards")
    @Operation(
        summary = "List validator block rewards",
        description =
            "Block rewards and performance per validator, newest block first by default. " +
                "Filter by validator, or by status: VALIDATED or MISSED.",
    )
    @AddressParameter(name = "validator", description = "Only this validator.")
    @BlockNumberParameter(
        `in` = ParameterIn.QUERY,
        required = false,
        description =
            "Start from this block: at or before it when `direction` is desc (default), at or" +
                " after it when asc.",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = BlockStatus::class),
        description = "VALIDATED or MISSED.",
        required = false,
    )
    @PaginationParameters
    @CommonApiResponses
    @CacheFor(CachePolicy.MINUTE)
    open fun getValidatorBlockRewards(
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) blockNumber: Long?,
        @RequestParam(required = false) status: BlockStatus?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ValidatorBlock> {
        val pageable = toPageable(page, size, direction, ValidatorBlock::blockNumber.name)
        return service.getValidatorBlockRewards(validator, blockNumber, status, pageable)
    }

    @GetMapping("/block-rewards/{blockNumber}")
    @Operation(
        summary = "Get validator block rewards for one block",
        description =
            "Every validator's reward record for one block. Pass `validator` for just one.",
    )
    @BlockNumberParameter(
        `in` = ParameterIn.PATH,
        required = true,
        description = "Block number.",
    )
    @AddressParameter(name = "validator", description = "Only this validator.")
    @CommonApiResponses
    @CacheFor(CachePolicy.MINUTE)
    open fun getBlockByBlockNumber(
        @PathVariable blockNumber: Long,
        @ValidAddress @RequestParam(required = false) validator: Address?,
    ): List<ValidatorBlock> = service.getBlockByNumber(blockNumber, validator)

    @GetMapping("/blocks/historic/{validator}")
    @Operation(
        summary = "Get validator VTHO rewards over time",
        description =
            "VTHO rewards between two times. Point spacing (hourly, daily, weekly or monthly)" +
                " depends on the length of the range, and the last point at or before each end is" +
                " included. Pass a validator address for one validator.",
    )
    @AddressParameter(
        name = "validator",
        `in` = ParameterIn.PATH,
        description = "Validator address.",
        required = true,
    )
    @AfterParameter(
        name = "startTimestamp",
        description = "Start time in Unix seconds, included.",
        required = true,
    )
    @BeforeParameter(
        name = "endTimestamp",
        description = "End time in Unix seconds, included.",
        required = true,
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getHistoricValidatorRewardsRange(
        @PathVariable @ValidAddress validator: Address,
        @RequestParam startTimestamp: Long,
        @RequestParam endTimestamp: Long,
    ): ResponseEntity<List<ValidatorBlock>> =
        cachedByAge(
            endTimestamp,
            service.getValidatorHistoricBlocks(
                startTimestamp,
                endTimestamp,
                validator.value.lowercase(),
            ),
        )

    // -- Deprecated: kept for client switch-over only. All legacy logic is inlined here so the
    // whole endpoint can be removed in one go alongside MissedBlocksTimeframe.kt and
    // ValidatorMissedBlocksStats.kt.
    @Deprecated("Use GET /api/v2/validators/slots")
    @GetMapping("/blocks/missed")
    @Operation(
        summary = "Get the share of blocks validators missed (deprecated)",
        description =
            """
            **Deprecated:** use `GET /api/v2/validators/slots`.

            The percentage of their scheduled block slots each validator missed over `timeframe`.
            Only validators that missed at least one are listed.
            """,
        deprecated = true,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "timeframe",
        schema = Schema(implementation = MissedBlocksTimeframe::class),
        description = "How far back to look.",
        required = true,
    )
    @AddressParameter(name = "validator", description = "Only this validator.")
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getMissedBlocksPercentage(
        @RequestParam timeframe: MissedBlocksTimeframe,
        @ValidAddress @RequestParam(required = false) validator: Address?,
    ): AllValidatorsMissedBlocksResponse {
        val days =
            when (timeframe) {
                MissedBlocksTimeframe.DAY -> 1L
                MissedBlocksTimeframe.WEEK -> 7L
                MissedBlocksTimeframe.MONTH -> 30L
                MissedBlocksTimeframe.YEAR -> 365L
            }
        val endTimestamp = System.currentTimeMillis() / 1000L
        val startTimestamp = (endTimestamp - days * 86_400L).coerceAtLeast(0L)
        val normalised = validator?.value?.let { HexUtils.normalise(it) }
        val stats =
            if (normalised != null) {
                listOfNotNull(
                    service.getSlotStatsForValidator(startTimestamp, endTimestamp, normalised)
                )
            } else {
                service.getSlotStats(startTimestamp, endTimestamp)
            }
        val endBlock = service.getCurrentBlockNumber()
        val startBlock = (endBlock - days * 8_640L).coerceAtLeast(0L)
        return AllValidatorsMissedBlocksResponse(
            timeframe = timeframe,
            startBlock = startBlock,
            endBlock = endBlock,
            validators =
                stats
                    .filter { it.missedSlots > 0L }
                    .map {
                        ValidatorMissedBlocksPercentage(
                            validator = it.validator,
                            missedPercentage = it.missedSlotRatio * 100.0,
                        )
                    },
        )
    }
}
