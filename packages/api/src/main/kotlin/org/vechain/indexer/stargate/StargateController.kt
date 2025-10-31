package org.vechain.indexer.stargate

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import java.math.BigInteger
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.STARGATE_PATH
import org.vechain.indexer.docs.BlockNumberParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlock
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlock
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlock
import org.vechain.indexer.thor.Address
import org.vechain.indexer.timeseries.TimeRangePreset
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.PaginationUtils
import org.vechain.indexer.validation.ValidAddress
import org.vechain.indexer.validation.ValidPageSize
import org.vechain.indexer.validation.ValidTimeRangePreset
import org.vechain.indexer.validation.ValidTokenLevel

@Profile("stargate")
@Tag(name = "Stargate", description = "Stargate related queries")
@Validated
@RestController
@RequestMapping(STARGATE_PATH)
open class StargateController(private val stargateService: StargateService) {
    @GetMapping("/total-vtho-claimed")
    @Operation(summary = "Get total VTHO claimed by Stargate users")
    @BlockNumberParameter(
        description =
            "Optional query parameter to get the total VTHO claimed at a specific block number. If not provided, the latest value will be returned."
    )
    @CommonApiResponses
    open fun getTotalVthoClaimed(@RequestParam(required = false) blockNumber: Long?): BigInteger =
        stargateService.getTotalVthoClaimed(blockNumber)

    @GetMapping("/total-vtho-claimed/{account}")
    @Operation(summary = "Get total VTHO claimed by a given account")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "account",
        schema = Schema(type = "string", pattern = Address.Companion.REGEX),
        description = "The account address to query for total VTHO claimed",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa",
    )
    @CommonApiResponses
    open fun getTotalVthoClaimed(@ValidAddress @PathVariable account: Address): BigInteger =
        stargateService.getTotalVthoClaimed(account.value)

    @GetMapping("/total-vtho-claimed/historic/{range}")
    @Operation(
        summary = "Get historic data for total VTHO claimed",
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
    open fun getTotalVthoClaimed(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String
    ): List<TimeSeriesRecord<BigInteger>> {
        val now = Instant.now()
        val range = TimeRangePreset.Companion.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)
        val before = now.epochSecond

        return stargateService.getTotalVthoClaimedHistoric(after, before)
    }

    @GetMapping("/nft-holders")
    @Operation(summary = "Get total number of NFT holders in Stargate")
    @BlockNumberParameter(
        description =
            "Optional query parameter to get the total number of NFT holders at a specific block number. If not provided, the latest value will be returned."
    )
    @CommonApiResponses
    open fun getNftHolders(@RequestParam(required = false) blockNumber: Long?): NftHoldersByBlock =
        stargateService.getNftHolders(blockNumber)
            ?: NftHoldersByBlock(
                blockId = "ignoredanyway",
                blockNumber = 0,
                blockTimestamp = 0,
                total = 0L,
                byLevel = emptyMap(),
            )

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
        val before = now.epochSecond

        return stargateService.getNftHoldersHistoric(
            after,
            before,
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
    ): VetStakedByBlock =
        stargateService.getTotalVetStaked(blockNumber)
            ?: VetStakedByBlock(
                blockId = "ignoredanyway",
                blockNumber = 0,
                blockTimestamp = 0,
                total = BigInteger.ZERO,
                byLevel = emptyMap(),
            )

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
        val before = now.epochSecond

        return stargateService.getTotalVetStakedHistoric(
            after,
            before,
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
        val before = now.epochSecond

        return stargateService.getTotalVthoGeneratedHistoric(after, before)
    }

    @GetMapping("/total-vet-delegated")
    @Operation(summary = "Get total VET delegated in Stargate")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema = Schema(type = "integer", format = "int64"),
        description =
            "Optional query parameter to get the total VET delegated at a specific block number. If not provided, the" +
                " latest value will be returned.",
        required = false,
        example = "12345678",
    )
    @CommonApiResponses
    open fun getTotalVetDelegated(
        @RequestParam(required = false) blockNumber: Long?
    ): VetDelegatedByBlock =
        stargateService.getTotalVetDelegated(blockNumber)
            ?: VetDelegatedByBlock(
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
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "tokenId",
        schema = Schema(type = "string", pattern = "^[A-Za-z0-9_.:-]+$"),
        description = "Optional query parameter to filter by token ID",
        required = false,
        example = "100001",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "manager",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Optional query parameter to filter by manager address",
        required = false,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "owner",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Optional query parameter to filter by owner address",
        required = false,
        example = "0x5cf3550e92971230210f6bfe8ad9dc323f2942f7",
    )
    @CommonApiResponses
    open fun getStargateTokens(
        @RequestParam(required = false) tokenId: String?,
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
        `in` = ParameterIn.PATH,
        name = "tokenId",
        schema = Schema(type = "string", pattern = "^[A-Za-z0-9_.:-]+$"),
        description = "The tokenId to query for rewards",
        required = true,
        example = "10001",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validator",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "Optional query parameter to filter by validator address",
        required = false,
        example = "0x5cf3550e92971230210f6bfe8ad9dc323f2942f7",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "periodType",
        description = "Reward period to filter by. Options: CYCLE, DAY, WEEK, MONTH, YEAR, ALL.",
        required = false,
    )
    @CommonApiResponses
    open fun getStargateTokenRewards(
        @PathVariable("tokenId") tokenId: String,
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
}
