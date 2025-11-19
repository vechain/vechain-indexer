package org.vechain.indexer.stargate

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import java.math.BigInteger
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.constants.STARGATE_PATH
import org.vechain.indexer.docs.AccountParameter
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockRepository
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockRepository
import org.vechain.indexer.thor.Address
import org.vechain.indexer.timeseries.TimeRangePreset
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTimeRangePreset
import org.vechain.indexer.validation.ValidTokenId
import org.vechain.indexer.validation.ValidTokenLevel

@Profile("stargate")
@Tag(name = "Stargate", description = "Stargate related queries")
@Validated
@RestController
@RequestMapping(STARGATE_PATH)
open class StargateController(
    private val stargateService: StargateService,
    private val vthoGeneratedByBlockRepository: VthoGeneratedByBlockRepository,
    private val vetStakedByBlockRepository: VetStakedByBlockRepository,
    private val vetDelegatedByBlockRepository: VetDelegatedByBlockRepository,
    private val nftHoldersByBlockRepository: NftHoldersByBlockRepository,
    private val vthoClaimedByBlockRepository: VthoClaimedByBlockRepository,
) {
    @GetMapping("/total-vtho-claimed")
    @Operation(summary = "Get total VTHO claimed by Stargate users")
    @BlockNumberParameter(
        description =
            "Optional query parameter to get the total VTHO claimed at a specific block number. If not provided, the latest value will be returned."
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "rewardsType",
        schema = Schema(type = "string", allowableValues = ["LEGACY", "DELEGATION"]),
        description =
            "Optional query parameter to filter rewards by type. If not all rewards will be returned.",
        required = false,
    )
    @CommonApiResponses
    open fun getTotalVthoClaimed(
        @RequestParam(required = false) blockNumber: Long?,
        @RequestParam(required = false) rewardsType: String?,
    ): BigInteger {
        val allowed = setOf("LEGACY", "DELEGATION", null, "")

        if (rewardsType !in allowed) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid rewardsType '$rewardsType'. Allowed values are: LEGACY, DELEGATION or empty.",
            )
        }

        return stargateService.getTotalVthoClaimed(blockNumber, rewardsType)
    }

    @GetMapping("/total-vtho-claimed/{account}")
    @Operation(summary = "Get total VTHO claimed by a given account")
    @AccountParameter(required = true, `in` = ParameterIn.PATH)
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "rewardsType",
        schema = Schema(type = "string", allowableValues = ["LEGACY", "DELEGATION"]),
        description =
            "Optional query parameter to filter rewards by type. If not provided, all types will be included.",
        required = false,
    )
    @CommonApiResponses
    open fun getTotalVthoClaimed(
        @ValidAddress @PathVariable account: Address,
        @RequestParam(required = false) rewardsType: String?,
    ): BigInteger {
        val allowed = setOf("LEGACY", "DELEGATION", null, "")

        if (rewardsType !in allowed) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid rewardsType '$rewardsType'. Allowed values are: LEGACY, DELEGATION or empty.",
            )
        }

        return stargateService.getTotalVthoClaimed(account.value, rewardsType)
    }

    @GetMapping("/total-vtho-claimed/{account}/{tokenId}")
    @Operation(summary = "Get total VTHO claimed by a given account and token ID")
    @AccountParameter(required = true, `in` = ParameterIn.PATH)
    @TokenIdParameter(required = true, `in` = ParameterIn.PATH)
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "rewardsType",
        schema = Schema(type = "string", allowableValues = ["LEGACY", "DELEGATION"]),
        description =
            "Optional query parameter to filter rewards by type. If not provided, all types will be included.",
        required = false,
    )
    @CommonApiResponses
    open fun getTotalVthoClaimed(
        @ValidAddress @PathVariable account: Address,
        @ValidTokenId @PathVariable tokenId: String,
        @RequestParam(required = false) rewardsType: String?,
    ): BigInteger {
        val allowed = setOf("LEGACY", "DELEGATION", null, "")

        if (rewardsType !in allowed) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid rewardsType '$rewardsType'. Allowed values are: LEGACY, DELEGATION or empty.",
            )
        }

        return stargateService.getTotalVthoClaimed(account.value, tokenId, rewardsType)
    }

    @GetMapping("/total-vtho-claimed/historic/{range}")
    @Operation(
        summary = "Get historic data for total VTHO claimed",
        description =
            "This endpoint returns a time series of total VTHO claimed by all Stargate users (Delegation only).",
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "range",
        schema =
            Schema(
                type = "string",
                allowableValues = arrayOf("1-hour", "1-day", "1-week", "1-month", "1-year", "all"),
            ),
        description = "Time range preset to use for the query.",
        required = true,
        example = "1-day",
    )
    @CommonApiResponses
    open fun getTotalVthoClaimed(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String
    ): List<TimeSeriesRecord<BigInteger>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)

        return stargateService.getTotalVthoClaimedHistoric(range, after)
    }

    @GetMapping("/nft-holders")
    @Operation(summary = "Get total number of NFT holders in Stargate")
    @BlockNumberParameter(
        description =
            "Optional query parameter to get the total number of NFT holders at a specific block number. If not provided, the latest value will be returned."
    )
    @CommonApiResponses
    open fun getNftHolders(
        @RequestParam(required = false) blockNumber: Long?
    ): NftHoldersByBlockDto = stargateService.getNftHolders(blockNumber)

    @GetMapping("/nft-holders/historic/{range}")
    @Operation(
        summary = "Get historic data for total NFT holders",
        description =
            "This endpoint returns a time series of NFT holders in Stargate. The time series is sparsely populated, " +
                "so it may not contain consistent gaps between records.",
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "range",
        schema =
            Schema(
                type = "string",
                allowableValues = arrayOf("1-hour", "1-day", "1-week", "1-month", "1-year", "all"),
            ),
        description = "Time range preset to use for the query.",
        required = true,
        example = "1-day",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "level",
        schema =
            Schema(
                type = "string",
                allowableValues =
                    [
                        "Strength",
                        "Thunder",
                        "Mjolnir",
                        "VeThorX",
                        "StrengthX",
                        "ThunderX",
                        "MjolnirX",
                        "Dawn",
                        "Lightning",
                        "Flash",
                    ],
            ),
        description =
            "Optional query parameter to filter NFT holders by level. If not provided, all levels will be included.",
        required = false,
    )
    @CommonApiResponses
    open fun getNftHolders(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String,
        @ValidTokenLevel @RequestParam(required = false) level: String? = null,
    ): List<TimeSeriesRecord<Long>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)

        return stargateService.getNftHoldersHistoric(
            after,
            range,
            TokenLevel.Companion.fromString(level),
        )
    }

    @GetMapping("/total-vet-staked")
    @Operation(summary = "Get total VET staked in Stargate")
    @BlockNumberParameter(
        description =
            "Optional query parameter to get the total VET staked at a specific block number. If not provided, the latest value will be returned."
    )
    @CommonApiResponses
    open fun getTotalVetStaked(
        @RequestParam(required = false) blockNumber: Long?
    ): TotalByBlockDto = stargateService.getTotalVetStaked(blockNumber)

    @GetMapping("/total-vet-staked/historic/{range}")
    @Operation(
        summary = "Get historic data for total VET staked",
        description =
            "This endpoint returns a time series of total VET staked in Stargate. The time series is sparsely " +
                "populated, so it may not contain consistent gaps between records.",
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "range",
        schema =
            Schema(
                type = "string",
                allowableValues = arrayOf("1-hour", "1-day", "1-week", "1-month", "1-year", "all"),
            ),
        description = "Time range preset to use for the query.",
        required = true,
        example = "1-day",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "level",
        schema =
            Schema(
                type = "string",
                allowableValues =
                    [
                        "Strength",
                        "Thunder",
                        "Mjolnir",
                        "VeThorX",
                        "StrengthX",
                        "ThunderX",
                        "MjolnirX",
                        "Dawn",
                        "Lightning",
                        "Flash",
                    ],
            ),
        description =
            "Optional query parameter to filter total VET staked by level. If not provided, all levels will be " +
                "included.",
        required = false,
    )
    @CommonApiResponses
    open fun getTotalVetStaked(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String,
        @ValidTokenLevel @RequestParam(required = false) level: String? = null,
    ): List<TimeSeriesRecord<BigInteger>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)

        return stargateService.getTotalVetStakedHistoric(
            after,
            range,
            TokenLevel.Companion.fromString(level),
        )
    }

    @GetMapping("/total-vtho-generated")
    @Operation(summary = "Get total VTHO generated by Stargate delelegations")
    @BlockNumberParameter(
        description =
            "Optional query parameter to get the total VTHO generated at a specific block number. If not provided, the latest value will be returned."
    )
    @CommonApiResponses
    open fun getTotalVthoGenerated(@RequestParam(required = false) blockNumber: Long?): BigInteger =
        stargateService.getTotalVthoGenerated(blockNumber)

    @GetMapping("/total-vtho-generated/historic/{range}")
    @Operation(
        summary = "Get historic data for total VTHO generated",
        description =
            "This endpoint returns a time series of total VTHO claimed by all Stargate users.",
    )
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "range",
        schema =
            Schema(
                type = "string",
                allowableValues = arrayOf("1-hour", "1-day", "1-week", "1-month", "1-year", "all"),
            ),
        description = "Time range preset to use for the query.",
        required = true,
        example = "1-day",
    )
    @CommonApiResponses
    open fun getTotalVthoGenerated(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String
    ): List<TimeSeriesRecord<BigInteger>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)
        val after = range.computeAfterTimestamp(now)

        return stargateService.getTotalVthoGeneratedHistoric(range, after)
    }

    @GetMapping("/total-vet-delegated")
    @Operation(summary = "Get total VET delegated in Stargate")
    @BlockNumberParameter
    @CommonApiResponses
    open fun getTotalVetDelegated(
        @ValidNonNegativeLong @RequestParam(required = false) blockNumber: Long?
    ): TotalByBlockDto =
        stargateService.getTotalVetDelegated(blockNumber)
            ?: TotalByBlockDto(
                blockId = "ignoredanyway",
                blockNumber = 0,
                blockTimestamp = 0,
                total = BigInteger.ZERO,
                byLevel = emptyMap(),
            )

    @GetMapping("/tokens")
    @Operation(
        summary = "Get Stargate Tokens",
        description =
            "Retrieve Stargate Token snapshots. You can filter results by tokenId, manager, owner, or any " +
                "combination of these parameters. If no filters are provided, all tokens will be returned.",
    )
    @TokenIdParameter
    @AccountParameter(name = "manager")
    @AccountParameter(name = "owner")
    @CommonApiResponses
    open fun getStargateTokens(
        @ValidTokenId @RequestParam(required = false) tokenId: String?,
        @ValidAddress @RequestParam(required = false) manager: Address?,
        @ValidAddress @RequestParam(required = false) owner: Address?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<StargateToken> {
        val pageable = PaginationUtils.toPageable(page, size, direction)
        return stargateService.getStargateTokens(
            tokenId,
            manager?.value?.lowercase(),
            owner?.value?.lowercase(),
            pageable,
        )
    }

    @GetMapping("/token-rewards/{tokenId}")
    @Operation(
        summary = "Get Stargate Token rewards",
        description =
            """
        Retrieve reward history for a given Stargate delegation token. 
        Rewards can be queried by period (CYCLE, DAY, WEEK, MONTH, YEAR). 
        All rewards are calculated and rolled over based on UTC time.
        
        If no `periodType` is provided, the response defaults to showing the full cumulative rewards.
    """,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "periodType",
        description = "Reward period to filter by. Options: CYCLE, DAY, WEEK, MONTH, YEAR, ALL.",
        required = false,
    )
    @TokenIdParameter(required = true, `in` = ParameterIn.PATH)
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validator",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Optional query parameter to filter by validator address",
        required = false,
        example = "0x5cf3550e92971230210f6bfe8ad9dc323f2942f7",
    )
    @CommonApiResponses
    open fun getStargateTokenRewards(
        @ValidTokenId @PathVariable("tokenId") tokenId: String,
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) periodType: RewardPeriod,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TokenReward> {
        val pageable =
            PaginationUtils.toPageable(page, size, direction, TokenReward::blockTimestamp.name)

        val rewards =
            stargateService.getRewards(tokenId, validator?.value?.lowercase(), periodType, pageable)
        return paginatedResponse(rewards)
    }

    @TimeFrameEndpoint
    @GetMapping("/vtho-generated/{period}")
    open fun getVthoGenerated(
        @PathVariable period: String,
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TotalByPeriodDto> {
        val tf = parseTimeFrame(period)
        val pageable = PaginationUtils.toPageable(page, size, direction)

        val slice =
            if (from != null || to != null) {
                stargateService.getTimeFrameDataRange(
                    from = from,
                    to = to,
                    timeFrame = tf,
                    pageable = pageable,
                    repository = vthoGeneratedByBlockRepository,
                )
            } else {
                stargateService.getTimeFrameData(
                    timeFrame = tf,
                    pageable = pageable,
                    direction = direction,
                    repository = vthoGeneratedByBlockRepository,
                )
            }

        return paginatedResponse(slice)
    }

    @TimeFrameEndpoint
    @GetMapping("/vtho-claimed/{period}")
    open fun getVthoClaimed(
        @PathVariable period: String,
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TotalByPeriodDto> {
        val tf = parseTimeFrame(period)
        val pageable = PaginationUtils.toPageable(page, size, direction)

        val slice =
            if (from != null || to != null) {
                stargateService.getTimeFrameDataRange(
                    from = from,
                    to = to,
                    timeFrame = tf,
                    pageable = pageable,
                    repository = vthoClaimedByBlockRepository,
                )
            } else {
                stargateService.getTimeFrameData(
                    timeFrame = tf,
                    pageable = pageable,
                    direction = direction,
                    repository = vthoClaimedByBlockRepository,
                )
            }

        return paginatedResponse(slice)
    }

    @TimeFrameEndpoint
    @GetMapping("/vet-delegated/{period}")
    open fun getVETDelegatedTimeFrame(
        @PathVariable period: String,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TotalByPeriodDto> {
        val tf = parseTimeFrame(period)
        val pageable = PaginationUtils.toPageable(page, size, direction)

        val slice =
            if (from != null || to != null) {
                stargateService.getTimeFrameDataRange(
                    from = from,
                    to = to,
                    timeFrame = tf,
                    pageable = pageable,
                    repository = vetDelegatedByBlockRepository,
                )
            } else {
                stargateService.getTimeFrameData(
                    timeFrame = tf,
                    pageable = pageable,
                    direction = direction,
                    repository = vetDelegatedByBlockRepository,
                )
            }

        return paginatedResponse(slice)
    }

    @TimeFrameEndpoint
    @GetMapping("/nft-holders/{period}")
    open fun getNFTHoldersTimeFrame(
        @PathVariable period: String,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TotalByPeriodDto> {
        val tf = parseTimeFrame(period)
        val pageable = PaginationUtils.toPageable(page, size, direction)

        val slice =
            if (from != null || to != null) {
                stargateService.getTimeFrameDataRange(
                    from = from,
                    to = to,
                    timeFrame = tf,
                    pageable = pageable,
                    repository = nftHoldersByBlockRepository,
                )
            } else {
                stargateService.getTimeFrameData(
                    timeFrame = tf,
                    pageable = pageable,
                    direction = direction,
                    repository = nftHoldersByBlockRepository,
                )
            }

        return paginatedResponse(slice)
    }

    @TimeFrameEndpoint
    @GetMapping("/vet-staked/{period}")
    open fun getVETStakedTimeFrame(
        @PathVariable period: String,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
    ): PaginatedResponse<TotalByPeriodDto> {
        val tf = parseTimeFrame(period)
        val pageable = PaginationUtils.toPageable(page, size, direction)

        val slice =
            if (from != null || to != null) {
                stargateService.getTimeFrameDataRange(
                    from = from,
                    to = to,
                    timeFrame = tf,
                    pageable = pageable,
                    repository = vetStakedByBlockRepository,
                )
            } else {
                stargateService.getTimeFrameData(
                    timeFrame = tf,
                    pageable = pageable,
                    direction = direction,
                    repository = vetStakedByBlockRepository,
                )
            }

        return paginatedResponse(slice)
    }

    private fun parseTimeFrame(input: String): TimeFrame? =
        try {
            TimeFrame.valueOf(input.uppercase())
        } catch (e: Exception) {
            if (input.equals("BLOCK", ignoreCase = true)) {
                null
            } else {
                throw IllegalArgumentException(
                    "Invalid period '$input'. Allowed: BLOCK, ${TimeFrame.entries.joinToString()}"
                )
            }
        }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Operation(
    summary = "Get Stargate metrics by time period",
    description =
        """
    Retrieve Stargate statistics aggregated by a preset time period, optionally filtered by a custom date range.

    ### Modes:
    **Preset Mode**  
    Pass `{period}` as one of: `DAY`, `WEEK`, `MONTH`, `YEAR`, `ALL`, or `BLOCK`.

    • `DAY`, `WEEK`, `MONTH`, `YEAR` — return aggregated summaries per period  
    • `BLOCK` — returns raw per-block totals  
    • `ALL` — returns a single all-time summary

    ### Custom Date Range Mode
    Pass `from` and/or `to` (Unix timestamps).

    • The `{period}` still controls the aggregation  
    • `from`/`to` simply filter which summarized records are returned  
      (e.g., DAY summaries within the range)

    ### Pagination:
    Supports `page`, `size`, and `direction`.
    """,
)
@Parameter(
    name = "period",
    `in` = ParameterIn.PATH,
    description = "Preset aggregation period",
    schema =
        Schema(type = "string", allowableValues = ["DAY", "WEEK", "MONTH", "YEAR", "ALL", "BLOCK"]),
    required = true,
)
@Parameter(
    name = "from",
    `in` = ParameterIn.QUERY,
    description = "Optional start of custom date range (Unix seconds)",
)
@Parameter(
    name = "to",
    `in` = ParameterIn.QUERY,
    description = "Optional end of custom date range (Unix seconds)",
)
@CommonApiResponses
annotation class TimeFrameEndpoint
