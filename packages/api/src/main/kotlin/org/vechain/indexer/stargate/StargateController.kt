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
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.constants.STARGATE_PATH
import org.vechain.indexer.docs.AddressParameter
import org.vechain.indexer.docs.AfterParameter
import org.vechain.indexer.docs.BeforeParameter
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.docs.RangeParameter
import org.vechain.indexer.docs.RewardsTypeParameter
import org.vechain.indexer.docs.StargateTokenHistoryEventNameParameter
import org.vechain.indexer.docs.TokenIdParameter
import org.vechain.indexer.docs.TokenLevelParameter
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.cachedFor
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.stargate.staking.NftHoldersReadRepository
import org.vechain.indexer.stargate.staking.VetStakedReadRepository
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedReadRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedReadRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedReadRepository
import org.vechain.indexer.thor.Address
import org.vechain.indexer.timeseries.TimeRangePreset
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.utils.TimeValidationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidNonNegativeLong
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidStargateTokenHistoryEventName
import org.vechain.indexer.validation.ValidTimeRangePreset
import org.vechain.indexer.validation.ValidTokenId
import org.vechain.indexer.validation.ValidTokenLevel

@Profile("stargate")
@Tag(name = "Stargate", description = "Stargate staking: NFTs, delegation and rewards.")
@Validated
@RestController
@RequestMapping(STARGATE_PATH)
open class StargateController(
    private val stargateService: StargateService,
    private val stargateTokenHistoryService: StargateTokenHistoryService,
    private val vthoGeneratedByBlockRepository: VthoGeneratedReadRepository,
    private val vetStakedByBlockRepository: VetStakedReadRepository,
    private val vetDelegatedByBlockRepository: VetDelegatedReadRepository,
    private val nftHoldersByBlockRepository: NftHoldersReadRepository,
    private val vthoClaimedRepository: VthoClaimedReadRepository,
) {
    @GetMapping("/total-vtho-claimed")
    @Operation(summary = "Get total VTHO claimed by Stargate users")
    @BlockNumberParameter(description = "Total as of this block. Defaults to the latest.")
    @RewardsTypeParameter(description = "Only this reward type. Defaults to all.")
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
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
    @AddressParameter(name = "account", required = true, `in` = ParameterIn.PATH)
    @RewardsTypeParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
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
    @AddressParameter(name = "account", required = true, `in` = ParameterIn.PATH)
    @TokenIdParameter(required = true, `in` = ParameterIn.PATH)
    @RewardsTypeParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
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
        summary = "Get total VTHO claimed over time",
        description = "Total VTHO claimed from Stargate delegation over time.",
    )
    @RangeParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTotalVthoClaimed(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String
    ): ResponseEntity<List<TimeSeriesRecord<BigInteger>>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)

        return cachedFor(
            range.cachePolicy,
            stargateService.getTotalVthoClaimedHistoric(range, after),
        )
    }

    @GetMapping("/nft-holders")
    @Operation(summary = "Get total number of NFT holders in Stargate")
    @BlockNumberParameter(description = "Total as of this block. Defaults to the latest.")
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getNftHolders(@RequestParam(required = false) blockNumber: Long?): NftHoldersDto =
        stargateService.getNftHolders(blockNumber)

    @GetMapping("/nft-holders/historic/{range}")
    @Operation(
        summary = "Get the number of NFT holders over time",
        description = "Number of Stargate NFT holders over time. Points are irregularly spaced.",
    )
    @RangeParameter
    @TokenLevelParameter(description = "Only this NFT level. Defaults to all levels.")
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getNftHolders(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String,
        @ValidTokenLevel @RequestParam(required = false) level: String? = null,
    ): ResponseEntity<List<TimeSeriesRecord<Long>>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)

        return cachedFor(
            range.cachePolicy,
            stargateService.getNftHoldersHistoric(
                after,
                range,
                TokenLevel.Companion.fromString(level),
            ),
        )
    }

    @GetMapping("/total-vet-staked")
    @Operation(summary = "Get total VET staked in Stargate")
    @BlockNumberParameter(description = "Total as of this block. Defaults to the latest.")
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTotalVetStaked(
        @RequestParam(required = false) blockNumber: Long?
    ): TotalByBlockDto = stargateService.getTotalVetStaked(blockNumber)

    @GetMapping("/total-vet-staked/historic/{range}")
    @Operation(
        summary = "Get total VET staked over time",
        description = "Total VET staked in Stargate over time. Points are irregularly spaced.",
    )
    @RangeParameter
    @TokenLevelParameter(description = "Only this NFT level. Defaults to all levels.")
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTotalVetStaked(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String,
        @ValidTokenLevel @RequestParam(required = false) level: String? = null,
    ): ResponseEntity<List<TimeSeriesRecord<BigInteger>>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)

        return cachedFor(
            range.cachePolicy,
            stargateService.getTotalVetStakedHistoric(
                after,
                range,
                TokenLevel.Companion.fromString(level),
            ),
        )
    }

    @GetMapping("/total-vtho-generated")
    @Operation(summary = "Get total VTHO generated by Stargate delegations")
    @BlockNumberParameter(description = "Total as of this block. Defaults to the latest.")
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTotalVthoGenerated(@RequestParam(required = false) blockNumber: Long?): BigInteger =
        stargateService.getTotalVthoGenerated(blockNumber)

    @GetMapping("/total-vtho-generated/historic/{range}")
    @Operation(
        summary = "Get total VTHO generated over time",
        description = "Total VTHO generated by Stargate delegations over time.",
    )
    @RangeParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTotalVthoGenerated(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String
    ): ResponseEntity<List<TimeSeriesRecord<BigInteger>>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)
        val after = range.computeAfterTimestamp(now)

        return cachedFor(
            range.cachePolicy,
            stargateService.getTotalVthoGeneratedHistoric(range, after),
        )
    }

    @GetMapping("/total-vet-delegated")
    @Operation(summary = "Get total VET delegated in Stargate")
    @BlockNumberParameter
    @CommonApiResponses
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getTotalVetDelegated(
        @ValidNonNegativeLong @RequestParam(required = false) blockNumber: Long?
    ): TotalByBlockDto = stargateService.getTotalVetDelegated(blockNumber)

    @GetMapping("/tokens")
    @Operation(
        summary = "List Stargate NFTs",
        description =
            "Stargate NFTs. Filter by `tokenId`, `manager`, `owner` or any mix of them; with " +
                "no filter, every NFT is returned.",
    )
    @TokenIdParameter
    @AddressParameter(name = "manager")
    @AddressParameter(name = "owner")
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.TEN_MINUTES)
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

    @GetMapping("/tokens/{tokenId}/history")
    @Operation(
        summary = "Get a Stargate NFT's history",
        description =
            "Everything that happened to one Stargate NFT: staking and delegation events, " +
                "transfers, sales and VeVote votes.",
    )
    @TokenIdParameter(required = true, `in` = ParameterIn.PATH)
    @StargateTokenHistoryEventNameParameter
    @AfterParameter
    @BeforeParameter
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.MINUTE)
    open fun getStargateTokenHistory(
        @ValidTokenId @PathVariable("tokenId") tokenId: String,
        @ValidStargateTokenHistoryEventName
        @RequestParam(name = "eventName", required = false)
        eventName: List<String>?,
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
            stargateTokenHistoryService.findTokenHistory(
                tokenId = tokenId,
                eventNames = eventName,
                before = before,
                after = after,
                pageable = pageable,
            )
        )
    }

    @GetMapping("/token-rewards/{tokenId}")
    @Operation(
        summary = "Get a Stargate NFT's rewards",
        description =
            "Delegation rewards earned by one Stargate NFT. `periodType` groups them by " +
                "CYCLE, DAY, WEEK, MONTH or YEAR (UTC days); leave it out for the running total.",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "periodType",
        description = "How to group rewards: CYCLE, DAY, WEEK, MONTH, YEAR or ALL.",
        required = false,
    )
    @TokenIdParameter(required = true, `in` = ParameterIn.PATH)
    @AddressParameter(name = "validator")
    @CommonApiResponses
    @PaginationParameters
    @CacheFor(CachePolicy.TEN_MINUTES)
    open fun getStargateTokenRewards(
        @ValidTokenId @PathVariable("tokenId") tokenId: String,
        @ValidAddress @RequestParam(required = false) validator: Address?,
        @RequestParam(required = false) periodType: RewardPeriod?,
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
    @CacheFor(CachePolicy.TEN_MINUTES)
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
    @CacheFor(CachePolicy.TEN_MINUTES)
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
                    repository = vthoClaimedRepository,
                )
            } else {
                stargateService.getTimeFrameData(
                    timeFrame = tf,
                    pageable = pageable,
                    direction = direction,
                    repository = vthoClaimedRepository,
                )
            }

        return paginatedResponse(slice)
    }

    @TimeFrameEndpoint
    @GetMapping("/vet-delegated/{period}")
    @CacheFor(CachePolicy.TEN_MINUTES)
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
    @CacheFor(CachePolicy.TEN_MINUTES)
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
    @CacheFor(CachePolicy.TEN_MINUTES)
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
                throw BadRequestException(
                    "Invalid period '$input'. Allowed: BLOCK, ${TimeFrame.entries.joinToString()}"
                )
            }
        }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Operation(
    summary = "Get Stargate statistics by period",
    description =
        """
        Stargate statistics grouped by `{period}`:

        - `HOUR`, `DAY`, `WEEK`, `MONTH`, `YEAR`: one summary per period
        - `BLOCK`: one entry per block
        - `ALL`: a single all-time summary

        `from` and `to` (Unix seconds) limit which periods are returned; they don't change the
        grouping.
        """,
)
@Parameter(
    name = "period",
    `in` = ParameterIn.PATH,
    description = "How to group the statistics.",
    schema =
        Schema(
            type = "string",
            allowableValues = ["HOUR", "DAY", "WEEK", "MONTH", "YEAR", "ALL", "BLOCK"],
        ),
    required = true,
)
@AfterParameter(name = "from", description = "Earliest period to return (Unix seconds).")
@BeforeParameter(name = "to", description = "Latest period to return (Unix seconds).")
@CommonApiResponses
@PaginationParameters
annotation class TimeFrameEndpoint
