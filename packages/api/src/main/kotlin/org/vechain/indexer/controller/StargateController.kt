package org.vechain.indexer.controller

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
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.TimeRangePreset
import org.vechain.indexer.model.TimeSeriesRecord
import org.vechain.indexer.model.stargate.NftHoldersByBlock
import org.vechain.indexer.model.stargate.TokenLevel
import org.vechain.indexer.model.stargate.VetStakedByBlock
import org.vechain.indexer.service.StargateService
import org.vechain.indexer.validation.ValidAddress
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
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema = Schema(type = "long"),
        description =
            "Optional query parameter to get the total VTHO claimed at a specific block number. If not provided, " +
                "the latest value will be returned.",
        required = false,
        example = "12345678",
    )
    open fun getTotalVthoClaimed(@RequestParam(required = false) blockNumber: Long?): BigInteger =
        stargateService.getTotalVthoClaimed(blockNumber)

    @GetMapping("/total-vtho-claimed/{account}")
    @Operation(summary = "Get total VTHO claimed by a given account")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "account",
        schema = Schema(type = "string", pattern = Address.REGEX),
        description = "The account address to query for total VTHO claimed",
        required = true,
        example = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa",
    )
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
    open fun getTotalVthoClaimed(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String
    ): List<TimeSeriesRecord<BigInteger>> {
        val now = Instant.now()
        val range = TimeRangePreset.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)
        val before = now.epochSecond

        return stargateService.getTotalVthoClaimedHistoric(after, before)
    }

    @GetMapping("/nft-holders")
    @Operation(summary = "Get total number of NFT holders in Stargate")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema = Schema(type = "long"),
        description =
            "Optional query parameter to get the total number of NFT holders at a specific block number. If not " +
                "provided, the latest value will be returned.",
        required = false,
        example = "12345678",
    )
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
    open fun getNftHolders(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String,
        @ValidTokenLevel @RequestParam(required = false) level: String? = null,
    ): List<TimeSeriesRecord<Long>> {
        val now = Instant.now()
        val range = TimeRangePreset.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)
        val before = now.epochSecond

        return stargateService.getNftHoldersHistoric(after, before, TokenLevel.fromString(level))
    }

    @GetMapping("/total-vet-staked")
    @Operation(summary = "Get total VET staked in Stargate")
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "blockNumber",
        schema = Schema(type = "long"),
        description =
            "Optional query parameter to get the total VET staked at a specific block number. If not provided, the" +
                " latest value will be returned.",
        required = false,
        example = "12345678",
    )
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
    open fun getTotalVetStaked(
        @ValidTimeRangePreset @PathVariable("range") rangeStr: String,
        @ValidTokenLevel @RequestParam(required = false) level: String? = null,
    ): List<TimeSeriesRecord<BigInteger>> {
        val now = Instant.now()
        val range = TimeRangePreset.fromPathValue(rangeStr)

        val after = range.computeAfterTimestamp(now)
        val before = now.epochSecond

        return stargateService.getTotalVetStakedHistoric(
            after,
            before,
            TokenLevel.fromString(level),
        )
    }
}
