@file:Suppress("DEPRECATION") // Mixes V1 (deprecated) and V1-only (block-rewards) endpoints.

package org.vechain.indexer.validator

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
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
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.SortFieldUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validators.AllValidatorsMissedBlocksResponse
import org.vechain.indexer.validators.MissedBlocksTimeframe
import org.vechain.indexer.validators.ValidatorMissedBlocksPercentage
import org.vechain.indexer.validators.ValidatorResponse
import org.vechain.indexer.validators.ValidatorService

@Profile("validator")
@Tag(name = "Validator", description = "Query validator documents")
@Validated
@RestController
@RequestMapping(VALIDATORS_PATH)
open class ValidatorController(private val service: ValidatorService) {
    @GetMapping
    @Operation(
        summary = "Get validators with optional filters (deprecated — use /api/v2/validators)",
        description =
            """
            **Deprecated:** Replaced by `GET /api/v2/validators`. This endpoint now reads from the
            V2 indexer (`validators_v2`) and reshapes the V2 document into the V1 wire format.
            `online` and `totalRewards` are returned as `null` (not populated on V2). `offlineBlocks`
            is sourced from V2's PoS-schedule misses and is numerically different from the V1
            transient `OfflineBlock` pointer. `sortBy=nft:<Level>` has no V2 equivalent and silently
            falls back to the default sort.

            This endpoint retrieves validator stats.

            You can filter the results by:
            - `validatorId`: (deprecated - use GET /api/v1/validators/{validatorId} instead)
            - `status`: validator status
            - `endorser`: endorser address

            You can also sort the results by one of the supported fields and paginate.

            - `sortBy`: Choose between `validatorTvl`, `totalTvl`, `blockProbability`, `delegatorTvl`, or `nft:<Level>` (projected next-cycle yield if that NFT were delegated, e.g. `nft:Strength`)
            - `page` and `size`: Controls pagination
            - `direction`: Either `asc` or `desc`
            """,
        deprecated = true,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validatorId",
        description = "Deprecated: use GET /api/v1/validators/{validatorId} instead.",
        required = false,
        deprecated = true,
        schema = Schema(type = "string", pattern = Address.REGEX),
    )
    @AddressParameter(name = "endorser", description = "Filter by endorser address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(type = "array", implementation = Status::class),
        description = "Filter by one or more validator statuses",
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "sortBy",
        description = "The sort by field",
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
        val pageable = toPageable(page, size, direction, sortField)

        val results =
            service.getValidators(
                validatorId = validatorId?.let { HexUtils.normalise(it) },
                endorser = endorser?.let { HexUtils.normalise(it) },
                statuses = status,
                pageable = pageable,
            )

        return paginatedResponse(results)
    }

    @GetMapping("/{validatorId}")
    @Operation(
        summary = "Get a single validator by ID (deprecated — use /api/v2/validators/{id})",
        description =
            "**Deprecated:** Replaced by `GET /api/v2/validators/{validatorId}`. " +
                "Returns a single validator's stats by their address.",
        deprecated = true,
    )
    @AddressParameter(
        name = "validatorId",
        `in` = ParameterIn.PATH,
        description = "Validator address",
        required = true,
    )
    @CommonApiResponses
    @PriceOracleUnavailableResponse
    open fun getValidatorById(@PathVariable @ValidAddress validatorId: Address): ValidatorResponse {
        val normalised = HexUtils.normalise(validatorId.value)
        return service.getValidatorById(normalised)
            ?: throw ResourceNotFoundException("Validator not found for id $normalised")
    }

    @Deprecated(
        "Use /api/v1/validators/block-rewards and /api/v1/validators/block-rewards/{blockNumber} instead"
    )
    @GetMapping("/blocks")
    @Operation(
        summary = "Get validator block records (deprecated)",
        description =
            "Note: the original description was inaccurate. This endpoint does not return cumulative " +
                "rewards 'up to the latest block'. It returns a paginated list of individual block " +
                "reward/miss records, optionally filtered by an exact block number, validator, or status. " +
                "Deprecated: use /api/v1/validators/block-rewards for paginated listing or /api/v1/validators/block-rewards/{blockNumber} for lookup by block.",
        deprecated = true,
    )
    @BlockNumberParameter(
        description =
            "Optional block number. If provided, returns the total VTHO rewards as of this block."
    )
    @AddressParameter(name = "validator", description = "Optional validator address")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = BlockStatus::class),
        description = "Filter by block status - either VALIDATED or MISSED.",
        required = false,
    )
    @PaginationParameters
    @CommonApiResponses
    open fun getValidatorBlocks(
        @RequestParam(required = false) blockNumber: Long?,
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) status: BlockStatus?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<ValidatorBlock> {
        val pageable = toPageable(page, size, direction, ValidatorBlock::blockNumber.name)
        return service.getValidatorBlocks(validator, status, blockNumber, pageable)
    }

    @GetMapping("/block-rewards")
    @Operation(
        summary = "Get paginated validator block reward records",
        description =
            "Returns a paginated list of validator block reward and performance records. " +
                "You can filter by validator address and/or block status (VALIDATED or MISSED). " +
                "Results are sorted by block number (default: descending).",
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @BlockNumberParameter(
        `in` = ParameterIn.QUERY,
        required = false,
        description =
            "Filter results by block number. When direction is 'desc' (default), returns records at or before this block. " +
                "When direction is 'asc', returns records at or after this block.",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = BlockStatus::class),
        description = "Filter by block status - either VALIDATED or MISSED.",
        required = false,
    )
    @PaginationParameters
    @CommonApiResponses
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
        summary = "Get validator block records for a specific block number",
        description =
            "Returns all validator block reward records for a specific block number. " +
                "You can optionally filter by validator address to narrow to a single record.",
    )
    @BlockNumberParameter(
        `in` = ParameterIn.PATH,
        required = true,
        description = "The block number to look up.",
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @CommonApiResponses
    open fun getBlockByBlockNumber(
        @PathVariable blockNumber: Long,
        @ValidAddress @RequestParam(required = false) validator: Address?,
    ): List<ValidatorBlock> = service.getBlockByNumber(blockNumber, validator)

    @GetMapping("/blocks/historic/{validator}")
    @Operation(
        summary = "Get historic VTHO rewards in a custom time range",
        description =
            "Returns a time series of VTHO rewards between the given timestamps. " +
                "Granularity (hourly/daily/weekly/monthly) is automatically chosen based on the time range. " +
                "For sampled ranges, the response includes the nearest records at or before the requested boundaries. " +
                "You can filter by validator address.",
    )
    @AddressParameter(
        name = "validator",
        `in` = ParameterIn.PATH,
        description = "Validator address",
        required = true,
    )
    @AfterParameter(
        name = "startTimestamp",
        description = "Start timestamp in Unix seconds (inclusive)",
        required = true,
    )
    @BeforeParameter(
        name = "endTimestamp",
        description = "End timestamp in Unix seconds (inclusive)",
        required = true,
    )
    @CommonApiResponses
    open fun getHistoricValidatorRewardsRange(
        @PathVariable @ValidAddress validator: Address,
        @RequestParam startTimestamp: Long,
        @RequestParam endTimestamp: Long,
    ): List<ValidatorBlock> =
        service.getValidatorHistoricBlocks(
            startTimestamp,
            endTimestamp,
            validator.value.lowercase(),
        )

    // -- Deprecated: kept for client switch-over only. All legacy logic is inlined here so the
    // whole endpoint can be removed in one go alongside MissedBlocksTimeframe.kt and
    // ValidatorMissedBlocksStats.kt.
    @Deprecated("Use GET /api/v2/validators/slots")
    @GetMapping("/blocks/missed")
    @Operation(
        summary = "Get missed blocks percentage for validators (deprecated)",
        description =
            "**Deprecated:** Replaced by `GET /api/v2/validators/slots`. `missedPercentage` is " +
                "`missedSlots / scheduledSlots * 100` over the window. `startBlock` and `endBlock` " +
                "are zeroed in this legacy shape — the underlying query now filters by timestamp.",
        deprecated = true,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "timeframe",
        schema = Schema(implementation = MissedBlocksTimeframe::class),
        description = "Time period to calculate missed blocks for",
        required = true,
    )
    @AddressParameter(name = "validator", description = "Optional validator address to filter by")
    @CommonApiResponses
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
                listOf(service.getSlotStatsForValidator(startTimestamp, endTimestamp, normalised))
            } else {
                service.getSlotStats(startTimestamp, endTimestamp)
            }
        return AllValidatorsMissedBlocksResponse(
            timeframe = timeframe,
            startBlock = 0L,
            endBlock = 0L,
            validators =
                stats.map {
                    ValidatorMissedBlocksPercentage(
                        validator = it.validator,
                        missedPercentage = it.missedSlotRatio * 100.0,
                    )
                },
        )
    }
}
